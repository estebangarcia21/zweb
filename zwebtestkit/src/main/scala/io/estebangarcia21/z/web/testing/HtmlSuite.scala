package io.estebangarcia21.z.web.testing

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import zio.*
import zio.http.Response

/** Synchronous boundary for existing pure MUnit and DOM suites. Effectful suites may use ZIO Test.
  */
class HtmlSuite extends munit.FunSuite:
  final case class HtmlPage(response: Response, document: Document)

  /** Consumes the body once and verifies the document content type. */
  def html(response: Response): HtmlPage =
    assert(response.rawHeader("Content-Type").exists(_.contains("text/html")))
    val body = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(response.body.asString).getOrThrowFiberFailure()
    }
    HtmlPage(response, Jsoup.parse(body))

  /** Requires exactly one semantic element, reporting the selector on failure. */
  def required(root: Element, selector: String): Element =
    val elements = root.select(selector)
    assertEquals(elements.size(), 1, s"Expected exactly one $selector")
    elements.first()
