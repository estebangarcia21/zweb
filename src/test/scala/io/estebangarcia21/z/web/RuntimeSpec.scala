package io.estebangarcia21.z.web

import zio.*
import zio.test.*
import zio.http.Status
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
    test("production does not expose live reload") {
      assertTrue(
        Zweb.liveReloadResponse(zio.http.Request.get("/_zweb/live-reload")).isEmpty,
        Zweb.liveReloadResponse(zio.http.Request.get("/_zweb/live-reload.js")).isEmpty
      )
    }
  )
