package io.estebangarcia21.z.web

import zio.*
import zio.http.*

import java.nio.file.{Path, Paths}
import java.util.UUID

/** Small server and development helpers over native ZIO HTTP routes. Application dependencies
  * remain in the ZIO environment and server/monitor resources belong to the serving scope.
  */
object Zweb:

  // Identifies a running JVM, including restarts whose compiled classes have not changed.
  private[web] val serverInstance: String = UUID.randomUUID().toString

  private[web] def isDevelopment: Boolean =
    setting("zweb.development", "ZWEB_DEVELOPMENT").contains("true")

  private[web] def liveReloadEnabled: Boolean =
    isDevelopment && setting("zweb.liveReload", "ZWEB_LIVE_RELOAD").contains("true")

  private[web] def reloadStampFile: Option[Path] =
    setting("zweb.reloadStampFile", "ZWEB_RELOAD_STAMP_FILE").map(Paths.get(_))

  private[web] def hotswapLogFile: Option[Path] =
    setting("zweb.hotswapLogFile", "ZWEB_HOTSWAP_LOG_FILE").map(Paths.get(_))

  private[web] def versionAssetUrl(path: String, version: String): String =
    val fragmentStart = path.indexOf('#')
    val (url, fragment) =
      if fragmentStart >= 0 then path.splitAt(fragmentStart)
      else (path, "")
    val separator = if url.contains('?') then "&" else "?"

    s"$url${separator}v=$version$fragment"

  private[web] def assetUrl(path: String): String =
    if isDevelopment then versionAssetUrl(path, serverInstance) else path

  /** Returns the application asset source directory supplied by the development tooling. */
  def developmentAssetDirectory: Option[Path] =
    Option
      .when(isDevelopment)(setting("zweb.assetDirectory", "ZWEB_ASSET_DIRECTORY"))
      .flatten
      .map(Paths.get(_))

  /** Compatibility adapter for applications that assemble their own server. Filesystem reads run on
    * the blocking executor. Prefer [[serve]], which installs reload routes automatically and shares
    * one scoped monitor across requests. Disabled outside explicitly enabled development.
    */
  def liveReloadResponse(request: Request): Option[Task[Response]] =
    if !liveReloadEnabled || request.method != Method.GET then None
    else
      request.url.path.encode match
        case "/_zweb/live-reload" =>
          Some(Development.readVersion().map(value => Development.noStore(Response.text(value))))
        case "/_zweb/live-reload.js" => Some(ZIO.succeed(reloadScriptResponse))
        case _                       => None

  private[web] def reloadScriptResponse: Response =
    Development.noStore(
      Response(body = Body.fromString(Layout.liveReloadJavaScript))
        .addHeader("Content-Type", "text/javascript; charset=utf-8")
    )

  /** Serves environment-dependent routes, adding refresh endpoints and no-store responses only in
    * development. Explicit ports take precedence; otherwise development uses the plugin port and
    * production uses 8080. Invalid configured ports fail in the effect before server startup.
    */
  def serve[R: EnvironmentTag](routes: Routes[R, Response]): ZIO[R, Throwable, Nothing] =
    resolvedPort.flatMap(port => serve(routes, port))

  /** Serves on an explicit port while preserving application requirements in `R`. */
  def serve[R: EnvironmentTag](routes: Routes[R, Response], port: Int): ZIO[R, Throwable, Nothing] =
    ZIO.scoped {
      for
        development <- if liveReloadEnabled then Development.make.map(Some(_)) else ZIO.none
        combined = development.fold(routes)(monitor => routes ++ monitor.routes)
        prepared =
          if isDevelopment then
            combined.transform(_.map(Development.noStore).mapError(Development.noStore))
          else combined
        result <- prepared
          .serve[R]
          .provideSomeLayer[R](Server.defaultWith(_.port(port).enableRequestStreaming))
      yield result
    }

  private[web] def resolvedPort: Task[Int] = ZIO.attempt {
    val port = if isDevelopment then setting("zweb.port", "ZWEB_PORT").fold(8080)(_.toInt) else 8080

    require(port >= 1 && port <= 65535, "Zweb port must be between 1 and 65535")
    port
  }

  private def setting(property: String, environment: String): Option[String] =
    sys.props.get(property).orElse(sys.env.get(environment)).filter(_.nonEmpty)
