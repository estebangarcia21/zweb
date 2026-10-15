package io.estebangarcia21.z.web.sbt

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File
import java.nio.file.Paths
import scala.util.{Failure, Success, Try}

private[sbt] final case class ZwebConfig(
    projectRoot: File,
    configFile: File,
    scalaPackage: Option[String],
    iconsOutput: Option[File],
    assetsDirectory: File,
    developmentPort: Int,
    developmentAutoRefresh: Boolean
)

private[sbt] object ZwebConfig {
  private val PackagePattern = "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*".r

  def load(projectRoot: File, configFile: File): Either[String, ZwebConfig] = {
    val loaded = Try {
      val root = projectRoot.getCanonicalFile
      val parsed =
        if (configFile.isFile) ConfigFactory.parseFile(configFile).resolve()
        else ConfigFactory.empty()
      val config =
        if (parsed.hasPath("zweb")) parsed.getConfig("zweb")
        else ConfigFactory.empty()
      val scalaPackage = optionalString(config, "scala-package")

      scalaPackage.foreach { value =>
        require(
          PackagePattern.pattern.matcher(value).matches(),
          "zweb.scala-package must be a valid dotted Scala package"
        )
      }

      val assetsDirectory =
        inside(root, string(config, "assets-directory", "src/main/resources/assets"))
      val iconsOutput = optionalString(config, "icons-output")
        .orElse(scalaPackage.map(value => s"src/main/scala/${value.replace('.', '/')}/Icons.scala"))
        .map(inside(root, _))
      val developmentPort = integer(config, "development.port", 8080)
      val developmentAutoRefresh = boolean(config, "development.auto-refresh", true)

      require(
        developmentPort >= 1 && developmentPort <= 65535,
        "zweb.development.port must be between 1 and 65535"
      )

      ZwebConfig(
        projectRoot = root,
        configFile = configFile.getCanonicalFile,
        scalaPackage = scalaPackage,
        iconsOutput = iconsOutput,
        assetsDirectory = assetsDirectory,
        developmentPort = developmentPort,
        developmentAutoRefresh = developmentAutoRefresh
      )
    }

    loaded match {
      case Success(config) => Right(config)
      case Failure(error)  =>
        Left(Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
    }
  }

  private def optionalString(config: Config, path: String): Option[String] =
    (if (config.hasPath(path)) Some(config.getString(path).trim) else None).filter(_.nonEmpty)

  private def string(config: Config, path: String, default: String): String =
    optionalString(config, path).getOrElse(default)

  private def integer(config: Config, path: String, default: Int): Int =
    if (config.hasPath(path)) config.getInt(path) else default

  private def boolean(config: Config, path: String, default: Boolean): Boolean =
    if (config.hasPath(path)) config.getBoolean(path) else default

  private def inside(root: File, configured: String): File = {
    val rootPath = root.toPath.toAbsolutePath.normalize()
    val configuredPath = Paths.get(configured)
    val resolved =
      if (configuredPath.isAbsolute) configuredPath.normalize()
      else rootPath.resolve(configuredPath).normalize()

    require(
      resolved.startsWith(rootPath),
      s"Zweb path must stay inside the project: $configured"
    )
    resolved.toFile
  }

}
