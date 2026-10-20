package io.estebangarcia21.z.web

import scalatags.Text.Frag
import scalatags.Text.all.*
import scalatags.Text.tags2.title

import java.nio.charset.StandardCharsets

/** Stylesheet and JavaScript module URLs rendered into an HTML document. */
final case class Assets(css: List[String] = Nil, js: List[String] = Nil)

/** Favicon metadata rendered into the document head. */
final case class Favicon(path: String, mediaType: String = "image/png")

/** Application-owned defaults shared by every rendered document.
  *
  * Zweb intentionally supplies no global CSS, JavaScript, or favicon by default. Applications opt
  * into those assets through one renderer rather than placing product-specific policy in the
  * library.
  */
final case class DocumentConfig(
    language: String = "en",
    globalAssets: Assets = Assets(),
    favicon: Option[Favicon] = None
)

/** Open Graph values rendered as `og:*` metadata for a complete page. */
final case class OpenGraph(
    title: String,
    description: String,
    url: String,
    image: Option[String] = None
)

/** Metadata and page-specific browser assets required for one complete HTML document. */
final case class PageMeta(
    title: String,
    assets: Assets = Assets(),
    robots: Option[String] = None,
    description: Option[String] = None,
    canonicalUrl: Option[String] = None,
    openGraph: Option[OpenGraph] = None
)

/** Anything that can render typed data to HTML. */
type View[-A] = A => Frag

/** Creates complete-document views with one application's shared document configuration. */
final class PageRenderer private (config: DocumentConfig):

  /** Combines pure metadata and body renderers into a complete document view. */
  def page[A](meta: A => PageMeta)(bodyView: View[A]): View[A] =
    data => Layout.render(config, meta(data), bodyView(data))

/** Constructs configured page renderers without retaining mutable global application state. */
object PageRenderer:

  def apply(config: DocumentConfig = DocumentConfig()): PageRenderer =
    new PageRenderer(config)

/** Combines pure metadata and body renderers using Zweb's asset-free defaults.
  *
  * Applications with global assets should construct one [[PageRenderer]] and export its `page`
  * method from an application-owned module.
  */
def page[A](meta: A => PageMeta)(bodyView: View[A]): View[A] =
  PageRenderer().page(meta)(bodyView)

/** Owns the generic HTML document skeleton and deterministic asset ordering. */
object Layout:

  /** Renders global assets before page-specific assets and versions them in development mode. */
  def render(config: DocumentConfig, metaData: PageMeta, content: Frag): Frag =
    frag(
      raw("<!doctype html>"),
      html(
        lang := config.language,
        head(
          meta(attr("charset") := "utf-8"),
          meta(
            attr("name") := "viewport",
            attr("content") := "width=device-width, initial-scale=1"
          ),
          metaData.robots.map(value => meta(attr("name") := "robots", attr("content") := value)),
          metaData.description.map(value =>
            meta(attr("name") := "description", attr("content") := value)
          ),
          title(metaData.title),
          metaData.canonicalUrl.map(value => link(rel := "canonical", href := value)),
          metaData.openGraph.map(openGraph =>
            frag(
              meta(attr("property") := "og:type", attr("content") := "website"),
              meta(attr("property") := "og:title", attr("content") := openGraph.title),
              meta(
                attr("property") := "og:description",
                attr("content") := openGraph.description
              ),
              meta(attr("property") := "og:url", attr("content") := openGraph.url),
              openGraph.image.map(value =>
                meta(attr("property") := "og:image", attr("content") := value)
              )
            )
          ),
          config.favicon.map(value =>
            link(
              rel := "icon",
              attr("type") := value.mediaType,
              href := Zweb.assetUrl(value.path)
            )
          ),
          config.globalAssets.css.map(stylesheet),
          metaData.assets.css.map(stylesheet),
          config.globalAssets.js.map(module),
          metaData.assets.js.map(module)
        ),
        body(
          content,
          Option.when(Zweb.liveReloadEnabled)(reloadScript(Zweb.serverInstance))
        )
      )
    )

  private[web] def reloadScript(version: String): Frag =
    script(src := "/_zweb/live-reload.js", attr("data-zweb-version") := version)

  // A same-origin external script works with script-src 'self'; hot refresh needs no inline exception.
  private[web] lazy val liveReloadJavaScript: String =
    val stream = getClass.getResourceAsStream("/zweb/live-reload.js")

    require(stream != null, "Missing Zweb development client")
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    finally stream.close()

  private def stylesheet(path: String): Frag =
    link(rel := "stylesheet", href := Zweb.assetUrl(path))

  private def module(path: String): Frag =
    script(src := Zweb.assetUrl(path), attr("type") := "module")
