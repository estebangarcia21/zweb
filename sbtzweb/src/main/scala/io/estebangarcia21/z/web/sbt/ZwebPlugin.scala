package io.estebangarcia21.z.web.sbt

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import spray.revolver.RevolverPlugin
import spray.revolver.RevolverCorePlugin.autoImport._
import spray.revolver.RevolverPlugin.autoImport._

import java.nio.file.Paths

/** Adds the matching Zweb runtime and testkit and registers the `zweb` command. */
object ZwebPlugin extends AutoPlugin {

  object autoImport {

    val zwebConfigFile =
      settingKey[File]("Project-local Zweb configuration file.")

    val zwebAddLibrary =
      settingKey[Boolean]("Whether Zweb adds its matching runtime and testkit dependencies.")

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
      reStart / fullClasspath ++= {
        // The agent reads this from the child classpath to watch sbt's freshly compiled classes.
        val configDirectory = target.value / "zweb-hot-config"
        IO.write(configDirectory / "hotswap-agent.properties", "autoHotswap=true\n")
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
        case Right(config) if arguments == Seq("dev") =>
          ZwebTooling.stopDevelopmentPort(config.developmentPort) match {
            case Left(error) =>
              state.log.error(error)
              state.fail
            case Right(messages) =>
              messages.foreach(message => state.log.info(message))
              Command.process("~reStart", state, error => state.log.error(error))
          }
        case Right(config) if arguments == Seq("dev", "hot") =>
          ZwebTooling
            .hotSwapJvmOptions(
              Paths.get(sys.props("java.home")),
              config.developmentAutoRefresh
            ) match {
            case Left(error) =>
              state.log.error(error)
              state.fail
            case Right(options) =>
              ZwebTooling.stopDevelopmentPort(config.developmentPort) match {
                case Left(error) =>
                  state.log.error(error)
                  state.fail
                case Right(messages) =>
                  messages.foreach(message => state.log.info(message))
                  val started = Command.process(
                    s"reStart --- ${options.mkString(" ")}",
                    state,
                    error => state.log.error(error)
                  )

                  val watchedTask =
                    if (config.developmentAutoRefresh) "~zwebHotCompile" else "~compile"

                  Command.process(watchedTask, started, error => started.log.error(error))
              }
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
