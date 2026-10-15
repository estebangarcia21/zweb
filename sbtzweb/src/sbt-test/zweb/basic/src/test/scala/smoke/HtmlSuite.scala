package smoke

class HtmlSuite extends io.estebangarcia21.z.web.testing.HtmlSuite:
  test("renders a ZIO HTTP response") {
    val response = io.estebangarcia21.z.web.html(Page.view(()))
    assertEquals(required(html(response).document, "h1").text(), "Zweb")
  }
