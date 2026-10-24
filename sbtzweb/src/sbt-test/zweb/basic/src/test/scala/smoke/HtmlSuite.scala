package smoke

class HtmlSuite extends io.estebangarcia21.z.web.testing.HtmlSuite:
  test("renders a ZIO HTTP response") {
    val response = io.estebangarcia21.z.web.html(Page.view(()))
    val document = html(response).document

    assertEquals(required(document, "h1").text(), "Zweb")
    assertEquals(required(document, "head > link[rel=manifest]").attr("href"), "/app.webmanifest")
    assertEquals(required(document, "head > meta[name=page-context]").attr("content"), "<safe> & sound")
    assertEquals(document.select("body link, body meta").size(), 0)
  }
