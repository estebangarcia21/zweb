package io.estebangarcia21.z.web.sbt

import sbt._
import sbt.Keys._
import sbt.nio.Keys.watchTriggers
import sbt.nio.file.Glob
import sbt.plugins.JvmPlugin
import spray.revolver.RevolverPlugin
import spray.revolver.RevolverCorePlugin.autoImport._
import spray.revolver.RevolverPlugin.autoImport._

import java.io.FileOutputStream
import java.nio.file.Paths
import java.util.Properties

/** Adds the matching Zweb runtime and testkit and registers the `zweb` command. */
object ZwebPlugin extends AutoPlugin {

  object autoImport {

    val zwebConfigFile =
      settingKey[File]("Project-local Zweb configuration file.")

    val zwebAddLibrary =
      settingKey[Boolean]("Whether Zweb adds its matching runtime and testkit dependencies.")

    val zwebDev =
      taskKey[Unit](
        "Compile and restart the managed application when code or configuration changes."
      )

    val zwebHotCompile =
      taskKey[Unit]("Compile sources and publish a browser reload version when classes change.")

  }

  import autoImport._

  override def requires: Plugins = JvmPlugin && RevolverPlugin

  override def trigger: PluginTrigger = noTrigger

  override def globalSettings: Seq[Def.Setting[_]] =
    Seq(commands += zwebCommand)

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      zwebConfigFile := baseDirectory.value / "zweb.conf",
      zwebAddLibrary := true,
      libraryDependencies ++= {
        if (zwebAddLibrary.value)
          Seq(
            "io.estebangarcia21.z" %% "zweb" % ZwebVersion.current,
            "io.estebangarcia21.z" %% "zwebtestkit" % ZwebVersion.current % Test
          )
        else Nil
      },
      reStart / aggregate := false,
      zwebDev / aggregate := false,
      zwebHotCompile / aggregate := false,
      zwebDev / watchTriggers += Glob(zwebConfigFile.value.toPath),
      zwebDev := Def.taskDyn {
        (Compile / compile).value

        val loaded = loadConfig(baseDirectory.value, zwebConfigFile.value)
        val classpath = (Runtime / fullClasspath).value.map(_.data)
        val resourceInputs = (Compile / resources).value.filterNot(file =>
          file.toPath.toAbsolutePath.normalize().startsWith(loaded.assetsDirectory.toPath)
        )
        val inputs = resourceInputs :+ loaded.configFile
        val version = classpath
          .map { file =>
            if (file.isDirectory) ZwebTooling.classTreeVersion(file.toPath)
            else s"${file.getAbsolutePath}:${file.length()}:${file.lastModified()}"
          }
          .mkString("\n") + inputs
          .map(file => s"${file.getAbsolutePath}:${file.length()}:${file.lastModified()}")
          .mkString("\n")
        val stamp = target.value / "zweb-dev.version"

        if (!stamp.isFile || IO.read(stamp) != version) Def.task {
          reStart.toTask("").value

          IO.write(stamp, version)
        }
        else Def.task { () }
      }.value,
      reStart / fullClasspath ++= {
        // The agent reads this from the child classpath to watch sbt's freshly compiled classes.
        val configDirectory = target.value / "zweb-hot-config"
        IO.createDirectory(configDirectory)
        val properties = new Properties()
        properties.setProperty("autoHotswap", "true")
        properties.setProperty("LOGGER.org.hotswap.agent.config.PluginManager", "debug")
        properties.setProperty("LOGFILE", (configDirectory / "agent.log").getAbsolutePath)
        val output = new FileOutputStream(configDirectory / "hotswap-agent.properties")

        try properties.store(output, "Zweb development agent")
        finally output.close()
        IO.write(
          configDirectory / "reload.stamp",
          ZwebTooling.classTreeVersion((Compile / classDirectory).value.toPath)
        )

        Seq(Attributed.blank(configDirectory))
      },
      zwebHotCompile := {
        (Compile / compile).value

        val configDirectory = target.value / "zweb-hot-config"
        val version = ZwebTooling.classTreeVersion((Compile / classDirectory).value.toPath)

        IO.write(configDirectory / "reload.stamp", version)
      },
      reStart / envVars ++= {
        val loaded = loadConfig(baseDirectory.value, zwebConfigFile.value)

        Map(
          "ZWEB_DEVELOPMENT" -> "true",
          "ZWEB_ASSET_DIRECTORY" -> loaded.assetsDirectory.getAbsolutePath
        )
      },
      reStart / javaOptions ++= {
        val loaded = loadConfig(baseDirectory.value, zwebConfigFile.value)

        Seq(
          "-Dzweb.development=true",
          s"-Dzweb.port=${loaded.developmentPort}",
          s"-Dzweb.liveReload=${loaded.developmentAutoRefresh}",
          s"-Dzweb.assetDirectory=${loaded.assetsDirectory.getAbsolutePath}",
          s"-Dzweb.reloadStampFile=${(target.value / "zweb-hot-config" / "reload.stamp").getAbsolutePath}"
        )
      }
    )

  private val zwebCommand =
    Command.args("zweb", "<command>") { (state, arguments) =>
      val extracted = Project.extract(state)
      val current = extracted.currentRef
      val base = extracted.get(current / baseDirectory)
      val configFile = extracted.get(current / zwebConfigFile)

      ZwebConfig.load(base, configFile) match {
        case Left(error) =>
          state.log.error(error)
          state.fail
        case Right(_) if arguments == Seq("dev") || arguments == Seq("dev", "restart") =>
          // Revolver owns its child process. Never terminate an unrelated port listener.
          IO.delete(extracted.get(current / target) / "zweb-dev.version")
          Command.process("~zwebDev", state, error => state.log.error(error))
        case Right(config) if arguments == Seq("dev", "hot") =>
          val childJavaHome = extracted
            .get(current / javaHome)
            .map(_.toPath)
            .getOrElse(Paths.get(sys.props("java.home")))

          ZwebTooling.hotSwapJvmOptions(childJavaHome, config.developmentAutoRefresh) match {
            case Left(error) =>
              state.log.warn(error + " Falling back to restart development.")
              IO.delete(extracted.get(current / target) / "zweb-dev.version")
              Command.process("~zwebDev", state, failure => state.log.error(failure))
            case Right(options) =>
              state.log.warn(
                "Hot swap is best effort: route tables, layers and configuration require zweb dev restart."
              )
              val logFile = extracted.get(current / target) / "zweb-hot-config" / "agent.log"
              state.log.info(s"Hot swap diagnostics: $logFile")
              val acknowledgedOptions =
                options :+ s"-Dzweb.hotswapLogFile=${logFile.getAbsolutePath}"
              val started = Command.process(
                s"reStart --- ${acknowledgedOptions.map(option => "\"" + option + "\"").mkString(" ")}",
                state,
                error => state.log.error(error)
              )

              Command.process("~zwebHotCompile", started, error => started.log.error(error))
          }
        case Right(config) =>
          ZwebTooling.run(arguments, config) match {
            case Left(error) =>
              state.log.error(error)
              state.fail
            case Right(messages) =>
              messages.foreach(message => state.log.info(message))
              state
          }
      }
    }

  private def loadConfig(base: File, configFile: File): ZwebConfig =
    ZwebConfig.load(base, configFile).fold(sys.error, identity)

}
