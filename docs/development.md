# Development

Zweb is a small Scala 3 / ZIO HTTP / ScalaTags foundation for server-rendered applications, with
HTMX for server interactions and Alpine.js for local browser state. It does not introduce a second
router or effect type. Browser dependencies are application-owned, opt-in assets managed by
`zweb js`. Project scaffolding is outside the current development-tooling scope.

## Application development

Enable `ZwebPlugin`, use `Zweb.serve(routes)`, and run:

```sh
sbt "zweb dev"
```

This works on JDK 21 without an IDE, agent, Node, or frontend build server. It compiles before
restarting the Revolver-managed child JVM. A compilation failure leaves the previous application
running and displays compiler diagnostics in the terminal; correcting the source resumes the watch.
Successful application changes restart the process, rebuilding routes and layers. Browser polling
retries while the server is unavailable and refreshes once the new process responds. There is no
fixed startup delay. A runtime startup failure is reported in sbt output; it does not restore the
previous JVM. The default watches compiled class directories, dependency JAR metadata, consumer non-asset
resources and `zweb.conf`; build-definition/dependency-setting changes require sbt `reload` and
re-entering the command. Resource and class fingerprints use names, sizes and modification times.

The configured asset directory is polled once per second by a scoped ZIO fiber, shared by all tabs.
Filesystem work uses the blocking executor. A failed scan logs a warning and retains the previous
snapshot. Asset-only edits do not restart the JVM:

- CSS changes replace same-origin stylesheet links after the replacement loads, retaining the
  document, unsaved forms and Alpine state. Failed loads retry; external stylesheets are untouched.
- JavaScript, images, fonts and other asset changes refresh the document. JavaScript module HMR and
  restoration of application state after a full document reload are not implemented.
- Creating/deleting files changes the asset version too. Metadata-preserving file rewrites are not
  detected. CSS imported by another CSS file is not independently cache-busted: serve development
  assets with `no-store`, as `Zweb.serve` does.

The application must serve assets from `Zweb.developmentAssetDirectory` in development, rather than
from a stale packaged resource copy. Static asset routing remains application-owned. `Zweb.serve`
sets `Cache-Control: no-store` on development responses, including assets and reload endpoints.
Production response caching is unchanged. Service workers that intercept development requests can
still hide updates; do not cache development responses in an application service worker.

Reload routes are installed automatically by `Zweb.serve` at `GET /_zweb/live-reload` and
`GET /_zweb/live-reload.js`. These reserved development paths take precedence over application
routes. The external same-origin script works with `script-src 'self'` and `connect-src 'self'`;
no inline-script exception is needed. `PageRenderer`/`page` includes it only when development and
live reload are enabled. Custom HTML documents must opt into the client themselves. Production
has neither the injected script nor automatic reload routes.

The browser initializes its asset/code baseline on its first successful poll and thereafter polls
once per second, with a five-second request timeout. Reload versions include the JVM instance ID,
so restarting unchanged code still refreshes connected documents. The version endpoint uses an
internal four-part text protocol; it is not a public application API.

Zweb never kills an arbitrary port listener. Revolver stops only its managed child when restarting.
If another process owns the port, startup fails with the server's bind error; stop that process
explicitly or select another port. Exit continuous watch to use sbt commands; `reStop` stops the
managed child. `zweb dev restart` explicitly starts the normal restart-based watch again.

## Optional JBR hot swapping

```sh
sbt "zweb dev hot"
```

This uses the consumer's `javaHome` (or sbt's JVM when unset), requiring a JBR 21 SDK with
`lib/hotswap/hotswap-agent.jar` and enhanced class redefinition. If the agent is absent, the command
warns and falls back to the normal restart-based watch with browser refresh. Presence of the JAR is
not a JVM compatibility guarantee: unsupported flags or agent startup failures remain launch errors.
The agent options are passed as quoted JVM arguments, including paths containing spaces.

Hot mode watches compilation, while browser code refresh waits for Hotswap Agent's successful
redefinition acknowledgement, not the compiler stamp or an arbitrary delay. Zweb enables the
agent's `PluginManager` debug logger and reads its completed `... reloaded classes` records from
`target/zweb-hot-config/agent.log`; the earlier `Reloading classes` record is not readiness. The
watch command prints this diagnostics path. Reads are bounded to the last 1 MiB, and the scoped
monitor retains its previous version when there is no complete acknowledgement in that window.
Agent errors remain in that log and do not advance the browser version. This integration is tested
with JBR 21.0.11 and Hotswap Agent 2.0.4-SNAPSHOT. Agents that change the acknowledgement format
require an adapter update; absent acknowledgements never fall back to guessing a completion delay.
Use restart mode if the agent cannot acknowledge a change. The log grows during a session and is
recreated by the agent on startup; it is disposable development output.

Method-body edits can therefore refresh the browser in the same JVM after redefinition. This is
still **best effort** for application semantics, not a guarantee that every Scala edit is live:
New/changed route tables, initialized values, acquired ZLayers, configuration and dependencies need
an application restart. Exit watch and run `zweb dev restart` for those changes. Long-lived fibers
and existing instances are not reconstructed by class redefinition. A failed hot compilation may
also have touched class files before failing; use restart mode when strict last-good isolation is
needed. The tested hot path is method-body changes, not arbitrary Scala 3 structural changes.

`development.auto-refresh = false` disables the injected browser client, polling endpoints and
asset monitor in either mode; compilation/restarts or class swapping continue. Restart watch after
changing this option in hot mode.

## ZIO and configuration boundaries

`Zweb.serve` accepts `Routes[R, Response]` and returns `ZIO[R, Throwable, Nothing]`. Supply application
layers at the outer application boundary. Domain errors remain typed in services/handlers; map them
to HTTP responses using native ZIO HTTP operators before serving. The server and development monitor
are scoped resources. Views remain `View[-A] = A => Frag`; no effect wrapper is required for pure
rendering. Document rendering no longer walks asset directories or reads compile stamps.

The plugin supplies system properties `zweb.development`, `zweb.assetDirectory`, `zweb.liveReload`,
`zweb.reloadStampFile`, and `zweb.port`; hot mode also supplies `zweb.hotswapLogFile`. Runtime environment alternatives are `ZWEB_DEVELOPMENT`,
`ZWEB_ASSET_DIRECTORY`, `ZWEB_LIVE_RELOAD`, `ZWEB_RELOAD_STAMP_FILE`, `ZWEB_PORT`,
and `ZWEB_HOTSWAP_LOG_FILE`; nonempty system
properties take precedence. These are process startup settings, not a dynamically reloadable
configuration API. Production ignores the development asset directory and port. `Zweb.serve(routes)`
uses the configured development port or 8080; `Zweb.serve(routes, port)` always honors the explicit
port. Invalid configured ports fail in the returned effect.

Applications assembling a custom server may retain `Zweb.liveReloadResponse(request)`. This
compatibility adapter scans on the blocking executor per version request, without the shared
monitor. Such applications own cache headers and endpoint routing. Prefer `Zweb.serve` for the
integrated lifecycle.

## Library development

The source build contains runtime `root`, DOM testkit `zwebtestkit`, and sbt plugin `sbtzweb`.
Use JDK 21 and Scala 3.8.4. Runtime dependencies are ZIO 2.1.26, ZIO HTTP 3.11.6 and ScalaTags 0.13.1.
The plugin uses sbt's Scala 2.12 and keeps its tool implementation independent of the runtime.

Run `./scripts/format.sc` and then `./scripts/formatCheck.sc` before finishing source changes.
They use sbt Scalafmt and dprint with a pinned JavaScript formatter. See [testing](testing.md) for
unit, plugin-consumer and real browser/watch checks. Do not run concurrent sbt processes compiling
this source checkout. Disposable consumer builds use separately published artifacts.

Local publication supports verification; `sbt publish` writes only to local
`target/distribution/maven`; no remote registry is configured. Commit and push library changes,
then update the same full Git commit SHA in each application's `build.sbt` and `project/plugins.sbt`.
A sibling checkout and `publishLocal` are not required by production consumers.
