package smoke

import io.estebangarcia21.z.web.{Zweb, html}
import zio.ZIOAppDefault
import zio.http.*

/** Compiles the minimal startup API without an explicit route-environment annotation. */
object Main extends ZIOAppDefault:

  def run = Zweb.serve(Routes(Method.GET / "" -> handler(html(Page.view(())))))
