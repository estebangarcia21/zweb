package io.estebangarcia21.z.web.sbt

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

import Support._

private[sbt] object JavaScriptTooling {
  private val NamePattern = "(?:@[a-z0-9._-]+/)?[a-z0-9._-]+".r
  private val VersionPattern = "[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?".r

  private final case class Dependency(
      name: String,
      npmPackage: String,
      version: String,
      sourcePath: String,
      fileName: String,
      sha256: String
  ) {

    def lockLine: String =
      List(name, npmPackage, version, sourcePath, fileName, sha256).mkString("\t")

  }

  private final case class KnownDependency(
      npmPackage: String,
      sourcePath: String,
      fileName: String
  )

  private val knownDependencies = Map(
    "htmx" -> KnownDependency("htmx.org", "dist/htmx.min.js", "htmx.min.js"),
    "htmx-head-support" -> KnownDependency(
      "htmx-ext-head-support",
      "dist/head-support.min.js",
      "htmx-head-support.min.js"
    ),
    "alpine" -> KnownDependency("alpinejs", "dist/cdn.min.js", "alpine.min.js"),
    "posthog-js" -> KnownDependency("posthog-js", "dist/array.js", "posthog-js.min.js")
  )

  def add(
      config: ZwebConfig,
      name: String,
      requestedVersion: Option[String],
      downloader: Downloader
  ): Either[String, Seq[String]] =
    for {
      _ <- either(
        NamePattern.pattern.matcher(name).matches(),
        "Dependency names must be lowercase npm package names or known aliases."
      )
      known = knownDependencies.get(name)
      npmPackage = known.map(_.npmPackage).getOrElse(name)
      version <- requestedVersion match {
        case Some(value) => Right(value)
        case None        => latestVersion(npmPackage, downloader)
      }
      _ <- either(
        VersionPattern.pattern.matcher(version).matches(),
        "Versions must be exact semantic versions, for example 2.0.10."
      )
      sourcePath <- known match {
        case Some(value) => Right(value.sourcePath)
        case None        => defaultSourcePath(npmPackage, version, downloader)
      }
      fileName = known.map(_.fileName).getOrElse(s"${safeName(name)}.min.js")
      dependency = Dependency(name, npmPackage, version, sourcePath, fileName, "")
      installed <- install(config, dependency, verifyHash = false, downloader)
      dependencies <- readLock(config)
      _ <- writeLock(config, dependencies.filterNot(_.name == name) :+ installed)
    } yield Seq(
      s"Pinned $name ${installed.version} at assets/js/vendor/${installed.fileName}."
    )

  def remove(config: ZwebConfig, name: String): Either[String, Seq[String]] =
    for {
      dependencies <- readLock(config)
      dependency <- dependencies.find(_.name == name).toRight(s"$name is not pinned.")
      _ <- attempt(Files.deleteIfExists(vendor(config).resolve(dependency.fileName))).map(_ => ())
      _ <- writeLock(config, dependencies.filterNot(_.name == name))
    } yield Seq(s"Removed $name.")

  def sync(
      config: ZwebConfig,
      downloader: Downloader
  ): Either[String, Seq[String]] =
    for {
      dependencies <- readLock(config)
      _ <- dependencies.foldLeft[Either[String, Unit]](Right(())) { (result, dependency) =>
        result.flatMap(_ => install(config, dependency, verifyHash = true, downloader).map(_ => ()))
      }
    } yield Seq(s"Restored ${dependencies.size} pinned JavaScript dependencies.")

  def list(config: ZwebConfig): Either[String, Seq[String]] =
    readLock(config).map { dependencies =>
      if (dependencies.isEmpty) Seq("No JavaScript dependencies are pinned.")
      else
        dependencies.sortBy(_.name).map(dependency => s"${dependency.name} ${dependency.version}")
    }

  private def install(
      config: ZwebConfig,
      dependency: Dependency,
      verifyHash: Boolean,
      downloader: Downloader
  ): Either[String, Dependency] = {
    val url =
      s"https://cdn.jsdelivr.net/npm/${dependency.npmPackage}@${dependency.version}/${dependency.sourcePath}"

    downloader.get(url).flatMap { bytes =>
      val actualHash = hash(bytes)

      if (verifyHash && actualHash != dependency.sha256)
        Left(
          s"Checksum mismatch for ${dependency.name}: expected ${dependency.sha256}, got $actualHash."
        )
      else
        write(vendor(config).resolve(dependency.fileName), bytes).map { _ =>
          dependency.copy(sha256 = actualHash)
        }
    }
  }

  private def latestVersion(
      npmPackage: String,
      downloader: Downloader
  ): Either[String, String] = {
    val url = s"https://data.jsdelivr.com/v1/package/npm/$npmPackage"

    text(url, downloader).flatMap { metadata =>
      val tagsPattern = "(?s)\"tags\"\\s*:\\s*\\{(.*?)\\}".r

      tagsPattern
        .findFirstMatchIn(metadata)
        .flatMap(result => jsonString(result.group(1), "latest"))
        .toRight(s"jsDelivr returned no latest version for $npmPackage.")
    }
  }

  private def defaultSourcePath(
      npmPackage: String,
      version: String,
      downloader: Downloader
  ): Either[String, String] = {
    val url = s"https://cdn.jsdelivr.net/npm/$npmPackage@$version/package.json"

    text(url, downloader).flatMap { packageJson =>
      jsonString(packageJson, "jsdelivr")
        .orElse(jsonString(packageJson, "unpkg"))
        .filter(_.endsWith(".js"))
        .toRight(
          s"$npmPackage@$version does not declare a jsDelivr or unpkg JavaScript entry."
        )
    }
  }

  private def text(url: String, downloader: Downloader): Either[String, String] =
    downloader.get(url).map(bytes => new String(bytes, StandardCharsets.UTF_8))

  private def jsonString(json: String, field: String): Option[String] = {
    val pattern =
      ("(?s)\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"").r

    pattern.findFirstMatchIn(json).map(_.group(1))
  }

  private def readLock(config: ZwebConfig): Either[String, Vector[Dependency]] = {
    val path = lock(config)

    if (!Files.isRegularFile(path)) Right(Vector.empty)
    else
      attempt(Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector).flatMap { lines =>
        lines
          .filter(line => line.nonEmpty && !line.startsWith("#"))
          .foldLeft[Either[String, Vector[Dependency]]](Right(Vector.empty)) { (result, line) =>
            line.split("\t", -1).toList match {
              case List(name, npmPackage, version, sourcePath, fileName, sha256) =>
                result.map(_ :+ Dependency(name, npmPackage, version, sourcePath, fileName, sha256))
              case _ =>
                Left(s"Invalid JavaScript dependency in $path: $line")
            }
          }
      }
  }

  private def writeLock(
      config: ZwebConfig,
      dependencies: Seq[Dependency]
  ): Either[String, Unit] = {
    val rows = dependencies.sortBy(_.name).map(_.lockLine)
    val contents =
      "# name\tnpm-package\tversion\tjsdelivr-path\tlocal-file\tsha256\n" +
        rows.mkString("\n") +
        (if (rows.isEmpty) "" else "\n")

    write(lock(config), contents.getBytes(StandardCharsets.UTF_8))
  }

  private def safeName(value: String): String =
    value.stripPrefix("@").replace('/', '-').replaceAll("[^A-Za-z0-9._-]", "-")

  private def vendor(config: ZwebConfig): Path =
    config.assetsDirectory.toPath.resolve("js/vendor")

  private def lock(config: ZwebConfig): Path =
    vendor(config).resolve("jsDeps.lock")

}
