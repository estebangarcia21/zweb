# Testing

`io.estebangarcia21.z.web.testing.HtmlSuite` extends MUnit for DOM assertions on ZIO HTTP responses.
Call `html(response).document` and `required(document, selector)`; the latter requires exactly one
match. Use specific selectors when a page contains repeated cards. Its synchronous runtime bridge
exists only at the test boundary. Production code stays effectful.

The runtime uses ZIO Test. Run `sbt test` for runtime, testkit and plugin unit tests. Run
`sbt publishLocal` followed by `sbt sbtzweb/scripted` for a real plugin consumer compilation and DOM
test. Local publication is private; no registry publication is configured.


## Development tooling checks

After runtime/plugin changes, run `sbt test`, then `sbt publishLocal` and
`sbt sbtzweb/scripted` sequentially. For browser-client or watch changes also run:

```sh
./scripts/testDevelopment.sc
./scripts/testDevelopment.sc hot
```

These checks require Scala CLI and Playwright Chromium. The script pins its test-only Playwright
Java dependency; it does not add browser tooling to the application runtime. Install Chromium with
`scala-cli run --dependency com.microsoft.playwright:playwright:1.62.0 --main-class com.microsoft.playwright.CLI -- install chromium`
if needed. Hot verification requires a compatible JBR SDK and agent; its assertion deliberately
fails if the command falls back to restart mode.

Each run creates a temporary consumer, uses locally published 0.1.0 artifacts, reserves an available
port, starts the real sbt watcher and drives Chromium. It verifies CSS replacement without losing
form/document/JVM state, JS document reload, Scala edits, added routes in restart mode, compilation
failure behavior and recovery, typed route environments, strict CSP compatibility and disabling
auto-refresh. It terminates only its own process tree and retains the fixture/log
path printed at completion for diagnosis. These checks do not use Squiggly Money or any database.
Publication must finish before starting a consumer test; run the two modes sequentially.

The runtime tests cover separated asset versions, deletion detection, successful agent acknowledgement (including incomplete/failed records), development port validation,
production endpoint exclusion, and document rendering (head composition, escaping, per-render page data, default output and
coexistence with development refresh). The scripted consumer checks custom head elements through
the DOM from a separately compiled application. The live checks complement these tests;
option-string tests alone are not proof that hot swapping works. Run `./scripts/format.sc` followed
by `./scripts/formatCheck.sc` for source formatting.
