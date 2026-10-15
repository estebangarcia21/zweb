# Migration from Paperweb

Zweb is a ZIO counterpart to Paperweb. Paperweb remains an independent project.
Use `io.estebangarcia21.z.web` and `io.estebangarcia21.z.web.testing.HtmlSuite`, enable
`ZwebPlugin`, and rename configuration and tooling directories to `zweb.conf` and `.zweb`.

ScalaTags views remain pure functions. Replace Cats Effect resources and http4s routes with ZIO
scopes and ZIO HTTP routes. `html(view)` produces a ZIO HTTP response; `Zweb.serve(routes, port)`
owns the HTTP server layer. There are no historical `paperweb.*` or `zweb.*` facade packages.
