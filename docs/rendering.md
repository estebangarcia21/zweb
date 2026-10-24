# Document rendering

Views are pure ScalaTags functions (`View[-A] = A => Frag`). `page` renders a complete HTML5
document with UTF-8 charset, viewport, language and title. `PageMeta` adds optional robots,
description, canonical URL, Open Graph values and page assets. `PageRenderer(DocumentConfig(...))`
provides application defaults without mutable global configuration.

## Additional head elements

Use `DocumentConfig.headContent` for shared head elements and `PageMeta.headContent` for elements
that depend on the page data. Both accept a ScalaTags `Frag` and default to an empty fragment.
Compose multiple elements with `frag(...)`; keep metadata values in ordinary ScalaTags attributes
so escaping remains intact. For example:

```scala
import io.estebangarcia21.z.web.*
import scalatags.Text.all.*

val renderer = PageRenderer(DocumentConfig(
  headContent = frag(
    link(rel := "manifest", href := "/app.webmanifest"),
    script(src := "/vendor/classic.js", defer)
  )
))

val view: View[String] = renderer.page[String](name => PageMeta(
  title = name,
  headContent = meta(attr("name") := "application-context", attr("content") := name)
))(name => div(name))
```

Head children render in this order: charset and viewport; robots and description; title;
canonical and Open Graph metadata; favicon; global CSS; page CSS; global module scripts;
page module scripts; shared `headContent`; page `headContent`. Each fragment preserves its own
element order. Additional content is additive: Zweb does not deduplicate elements or override
standard metadata. Use the dedicated metadata fields for values Zweb already understands.
Do not supply a nested `head`, `html` or `body` element.

Fragments are part of the ScalaTags document tree, not pre-rendered strings. Rendering never
searches for closing tags or rewrites body content. Page fragments are computed with metadata
for each view invocation. Supply trusted element structure, use escaped text/attributes for
untrusted values, and reserve `raw` for intentionally trusted markup. Zweb does not sanitize
arbitrary fragments or make inline scripts compatible with an application's CSP.

`Assets.js` emits module scripts. Use head fragments when a classic/deferred script or other
custom attributes are required. Development automatically versions URLs in `Assets` and the
configured favicon only; fragment URLs are emitted as supplied. The development reload script
still renders after body content when enabled. Applications own CSP, asset serving, caching,
installation metadata and product-specific browser configuration.
