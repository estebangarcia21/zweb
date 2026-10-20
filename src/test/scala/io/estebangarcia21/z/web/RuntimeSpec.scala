package io.estebangarcia21.z.web

import zio.*
import zio.test.*
import zio.http.{Request, Status}

import java.nio.file.Files
import scala.jdk.CollectionConverters.*
import scalatags.Text.all.*

/** Verifies rendering, HTML encoding, and safe asset URL versioning. */
object RuntimeSpec extends ZIOSpecDefault:

  def spec = suite("Zweb runtime")(
    test("renders escaped content and UTF-8 HTML") {
      val view = page[String](_ => PageMeta("Example"))(name => div(name))
      val response = io.estebangarcia21.z.web.html(view("<script>"))
      response.body.asString.map(body =>
        assertTrue(
          response.status == Status.Ok,
          response.rawHeader("Content-Type").exists(_.contains("text/html")),
          body.contains("&lt;script&gt;"),
          body.contains("Example")
        )
      )
    },
    test("retains existing query and fragment when versioning assets") {
      assertTrue(
        Zweb.versionAssetUrl("/app.js?a=1#anchor", "42") == "/app.js?a=1&v=42#anchor",
        Zweb.versionAssetUrl("/app.css", "42") == "/app.css?v=42"
      )
    },
    test("hot refresh uses an external same-origin script without an inline CSP exception") {
      val fragment = Layout.reloadScript("test-version").render
      assertTrue(
        fragment.contains("src=\"/_zweb/live-reload.js\""),
        fragment.contains("data-zweb-version=\"test-version\""),
        !fragment.contains("(() =>"),
        Layout.liveReloadJavaScript.contains("credentials: \"same-origin\"")
      )
    },
    test("CSS and non-CSS asset versions change independently, including deletions") {
      ZIO.attemptBlocking {
        val directory = Files.createTempDirectory("zweb-assets-")

        try
          val css = directory.resolve("app.css")
          val js = directory.resolve("app.js")
          Files.writeString(css, "body {}")
          Files.writeString(js, "first")
          val initial = Development.assetVersions(directory)

          Files.writeString(css, "body { color: red; }")
          val styled = Development.assetVersions(directory)
          Files.delete(js)
          val removed = Development.assetVersions(directory)

          assertTrue(
            initial._1 != styled._1,
            initial._2 == styled._2,
            styled._1 == removed._1,
            styled._2 != removed._2
          )
        finally
          val paths = Files.walk(directory)

          try paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
          finally paths.close()
      }
    },
    test("hot refresh waits for a complete successful agent acknowledgement") {
      ZIO.attemptBlocking {
        val log = Files.createTempFile("zweb-agent-", ".log")
        val prefix = "HOTSWAP AGENT: 12:00:00.000 DEBUG (org.hotswap.agent.config.PluginManager) - "

        try
          Files.writeString(log, prefix + "Reloading classes [Example] (autoHotswap)\n")
          val before = Development.acknowledgedVersion(log, "previous")
          Files.writeString(log, prefix + "... reloaded classes [Example] (autoHotswap)")
          val incomplete = Development.acknowledgedVersion(log, "previous")
          Files.writeString(log, prefix + "... reloaded classes [Example] (autoHotswap)\n")
          val ready = Development.acknowledgedVersion(log, "previous")
          Files.writeString(log, prefix + "... Fail to reload classes [Example]\n")
          val failed = Development.acknowledgedVersion(log, ready)

          assertTrue(
            before == "previous",
            incomplete == "previous",
            ready != "previous",
            failed == ready
          )
        finally Files.deleteIfExists(log)
      }
    },
    test("development honors the configured port and environment-compatible reload endpoints") {
      withProperties(
        Map("zweb.development" -> "true", "zweb.liveReload" -> "true", "zweb.port" -> "9191")
      ) {
        for
          port <- Zweb.resolvedPort
          response <- Zweb.liveReloadResponse(Request.get("/_zweb/live-reload")).get
          body <- response.body.asString
        yield assertTrue(
          port == 9191,
          body.startsWith(Zweb.serverInstance + ":"),
          body.split(":").length == 4,
          response.rawHeader("Cache-Control").contains("no-store")
        )
      }
    },
    test("invalid development ports fail in the effect") {
      withProperties(Map("zweb.development" -> "true", "zweb.port" -> "invalid")) {
        Zweb.resolvedPort.exit.map(exit => assertTrue(exit.isFailure))
      }
    },
    test("production does not expose live reload") {
      assertTrue(
        Zweb.liveReloadResponse(zio.http.Request.get("/_zweb/live-reload")).isEmpty,
        Zweb.liveReloadResponse(zio.http.Request.get("/_zweb/live-reload.js")).isEmpty
      )
    }
  ) @@ TestAspect.sequential

  private def withProperties[A](values: Map[String, String])(effect: Task[A]): Task[A] =
    ZIO.acquireReleaseWith(ZIO.attempt {
      val previous = values.keys.map(key => key -> sys.props.get(key)).toMap

      values.foreach { case (key, value) => sys.props.put(key, value) }
      previous
    })(previous =>
      ZIO.succeed {
        previous.foreach {
          case (key, Some(value)) => sys.props.put(key, value)
          case (key, None)        => sys.props.remove(key)
        }
      }
    )(_ => effect)
