package io.estebangarcia21.z.web.sbt

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._

import Support._

private[sbt] object ZwebTooling {

  val help: Seq[String] = Seq(
    "Zweb commands:",
    "  zweb init <scala-package>",
    "  zweb dev",
    "  zweb dev hot",
    "  zweb dev restart",
    "  zweb doctor",
    "  zweb icon add <lucide-name>",
    "  zweb icon remove <lucide-name>",
    "  zweb icon list",
    "  zweb icon sync",
    "  zweb js add <name> [version]",
    "  zweb js remove <name>",
    "  zweb js list",
    "  zweb js sync"
  )

  def run(
      arguments: Seq[String],
      config: ZwebConfig,
      downloader: Downloader = HttpDownloader
  ): Either[String, Seq[String]] =
    arguments.toList match {
      case Nil | "help" :: Nil =>
        Right(help)
      case "init" :: scalaPackage :: Nil =>
        initialize(config, scalaPackage)
      case "doctor" :: Nil =>
        doctor(config)
      case "icon" :: "add" :: name :: Nil =>
        IconTooling.add(config, name, downloader)
      case "icon" :: "remove" :: name :: Nil =>
        IconTooling.remove(config, name)
      case "icon" :: "list" :: Nil =>
        IconTooling.list(config)
      case "icon" :: "sync" :: Nil =>
        IconTooling.sync(config, downloader)
      case "js" :: "add" :: name :: Nil =>
        JavaScriptTooling.add(config, name, None, downloader)
      case "js" :: "add" :: name :: version :: Nil =>
        JavaScriptTooling.add(config, name, Some(version), downloader)
      case "js" :: "remove" :: name :: Nil =>
        JavaScriptTooling.remove(config, name)
      case "js" :: "list" :: Nil =>
        JavaScriptTooling.list(config)
      case "js" :: "sync" :: Nil =>
        JavaScriptTooling.sync(config, downloader)
      case _ =>
        Left("Unknown Zweb command. Run sbt \"zweb help\" for usage.")
    }

  def hotSwapJvmOptions(javaHome: Path, autoRefresh: Boolean): Either[String, Seq[String]] = {
    val agent = javaHome.resolve("lib/hotswap/hotswap-agent.jar")

    if (!Files.isRegularFile(agent))
      Left(
        s"Hot development requires a JBR 21 SDK with Hotswap Agent at $agent. " +
          "Use zweb dev for restart-based development."
      )
    else
      Right(
        Seq(
          s"-Dzweb.liveReload=$autoRefresh",
          "-XX:+AllowEnhancedClassRedefinition",
          "-XX:HotswapAgent=external",
          s"-javaagent:${agent.toAbsolutePath}=autoHotswap=true,disablePlugin=AnonymousClassPatch"
        )
      )
  }

  def classTreeVersion(directory: Path): String = {
    if (!Files.isDirectory(directory)) "0"
    else {
      val files = Files.walk(directory)

      try {
        val digest = MessageDigest.getInstance("SHA-256")

        files
          .iterator()
          .asScala
          .filter(path => Files.isRegularFile(path) && path.toString.endsWith(".class"))
          .toVector
          .sortBy(_.toString)
          .foreach { path =>
            val entry =
              s"${directory.relativize(path)}:${Files.size(path)}:${Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS)}\n"

            digest.update(entry.getBytes(StandardCharsets.UTF_8))
          }

        HexFormat.of().formatHex(digest.digest())
      } finally files.close()
    }
  }

  private def initialize(
      config: ZwebConfig,
      scalaPackage: String
  ): Either[String, Seq[String]] = {
    val validPackage =
      scalaPackage.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*")

    if (config.configFile.exists())
      Left(s"${config.configFile.getName} already exists.")
    else if (!validPackage)
      Left("The Scala package must be a valid dotted package name.")
    else {
      val contents =
        s"""zweb {
           |  scala-package = "$scalaPackage"
           |  assets-directory = "src/main/resources/assets"
           |
           |  development {
           |    port = 8080
           |    auto-refresh = true
           |  }
           |}
           |""".stripMargin

      write(config.configFile.toPath, contents.getBytes(StandardCharsets.UTF_8)).map { _ =>
        Seq(s"Created ${config.configFile.getName} for $scalaPackage.")
      }
    }
  }

  private def doctor(config: ZwebConfig): Either[String, Seq[String]] = {
    val configState =
      if (config.configFile.isFile) s"configuration: ${config.configFile.getPath}"
      else s"configuration: missing ${config.configFile.getPath}"
    val packageState =
      config.scalaPackage.fold("Scala package: not configured")(value => s"Scala package: $value")
    val iconState =
      config.iconsOutput.fold("icons output: not configured")(value =>
        s"icons output: ${value.getPath}"
      )

    Right(
      Seq(
        configState,
        packageState,
        iconState,
        s"assets directory: ${config.assetsDirectory.getPath}",
        s"development port: ${config.developmentPort}",
        s"development auto-refresh: ${config.developmentAutoRefresh}"
      )
    )
  }

}
