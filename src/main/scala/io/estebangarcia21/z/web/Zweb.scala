package io.estebangarcia21.z.web

import zio.*
import zio.http.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.concurrent.duration.Duration
import scala.util.Try

/** Runtime helpers shared by Zweb applications.
  *
  * Development mode versions asset URLs once per server start; production behavior remains
  * cache-friendly and graceful. The sbt plugin supplies development settings through system
  * properties and environment variables.
  */
object Zweb:

  private lazy val developmentAssetVersion =
    Option.when(isDevelopment)(java.lang.System.currentTimeMillis().toString)

  private def isDevelopment: Boolean =
    setting("zweb.development", "ZWEB_DEVELOPMENT").contains("true")

  private[web] def liveReloadEnabled: Boolean =
    isDevelopment && sys.props.get("zweb.liveReload").contains("true")

  private[web] def liveReloadVersion: String =
    val compiled = setting("zweb.reloadStampFile", "ZWEB_RELOAD_STAMP_FILE")
      .flatMap(path => Try(Files.readString(Paths.get(path)).trim).toOption)
      .getOrElse("0")
    val assets = developmentAssetDirectory
      .flatMap(path => Try(assetTreeVersion(path)).toOption)
      .getOrElse("0")

    s"$compiled:$assets"

  private[web] def assetTreeVersion(directory: Path): String =
    if !Files.isDirectory(directory) then "0"
    else
      val files = Files.walk(directory)

      try
        val digest = MessageDigest.getInstance("SHA-256")

        files
          .iterator()
          .asScala
          .filter(path => Files.isRegularFile(path))
          .toVector
          .sortBy(_.toString)
          .foreach { path =>
            val entry =
              s"${directory.relativize(path)}:${Files.size(path)}:${Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS)}\n"

            digest.update(entry.getBytes(StandardCharsets.UTF_8))
          }

        HexFormat.of().formatHex(digest.digest())
      finally files.close()

  private[web] def versionAssetUrl(path: String, version: String): String =
    val fragmentStart = path.indexOf('#')
    val (url, fragment) =
      if fragmentStart >= 0 then path.splitAt(fragmentStart)
      else (path, "")
    val separator = if url.contains('?') then "&" else "?"
    s"$url${separator}v=$version$fragment"

  private[web] def assetUrl(path: String): String =
    developmentAssetVersion.fold(path)(versionAssetUrl(path, _))

  /** Returns the application asset source directory supplied by the development tooling. */
  def developmentAssetDirectory: Option[Path] =
    setting("zweb.assetDirectory", "ZWEB_ASSET_DIRECTORY").map(Paths.get(_))

  /** Exposes the current compiled-code and asset version only in hot development mode.
    *
    * Applications add these routes to their HTTP route assembly. The response is never cached, and
    * ordinary development and production modes expose no reload endpoint.
    */
  def liveReloadResponse(request: Request): Option[Task[Response]] =
    if !liveReloadEnabled || request.method != Method.GET then None
    else
      request.url.path.encode match
        case "/_zweb/live-reload" =>
          Some(
            ZIO
              .attempt(liveReloadVersion)
              .map(value => Response.text(value).addHeader("Cache-Control", "no-store"))
          )
        case "/_zweb/live-reload.js" =>
          Some(
            ZIO.succeed(
              Response(body = Body.fromString(Layout.liveReloadJavaScript))
                .addHeader("Content-Type", "text/javascript; charset=utf-8")
                .addHeader("Cache-Control", "no-store")
            )
          )
        case _ => None

  /** Runs the HTTP application with resources owned by the ZIO server layer. */
  def serve(routes: Routes[Any, Response], port: Int = 8080): ZIO[Any, Throwable, Nothing] =
    Server.serve(routes).provide(Server.defaultWith(_.port(port).enableRequestStreaming))

  private def setting(property: String, environment: String): Option[String] =
    sys.props.get(property).orElse(sys.env.get(environment)).filter(_.nonEmpty)
