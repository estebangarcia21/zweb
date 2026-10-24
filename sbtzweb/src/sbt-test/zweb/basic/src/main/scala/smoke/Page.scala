package smoke

import io.estebangarcia21.z.web.*
import scalatags.Text.all.*
import scalatags.Text.tags2.{main => mainTag}

/** Exercises the public rendering API from a separately compiled application. */
object Page:
  private val renderer = PageRenderer(DocumentConfig(
    headContent = link(rel := "manifest", href := "/app.webmanifest")
  ))

  val view: View[Unit] =
    renderer.page[Unit](_ => PageMeta(
      "Smoke test",
      headContent = meta(attr("name") := "page-context", attr("content") := "<safe> & sound")
    ))(_ => mainTag(h1("Zweb")))
