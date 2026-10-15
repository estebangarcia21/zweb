# Testing

`io.estebangarcia21.z.web.testing.HtmlSuite` extends MUnit for DOM assertions on ZIO HTTP responses.
Call `html(response).document` and `required(document, selector)`; the latter requires exactly one
match. Use specific selectors when a page contains repeated cards. Its synchronous runtime bridge
exists only at the test boundary. Production code stays effectful.

The runtime uses ZIO Test. Run `sbt test` for runtime, testkit and plugin unit tests. Run
`sbt publishLocal` followed by `sbt sbtzweb/scripted` for a real plugin consumer compilation and DOM
test. Local publication is private; no registry publication is configured.
