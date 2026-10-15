package smoke

import io.estebangarcia21.z.web.*
import scalatags.Text.all.*
import scalatags.Text.tags2.{main => mainTag}

object Page:
  val view: View[Unit] =
    page(_ => PageMeta("Smoke test"))(_ => mainTag(h1("Zweb")))
