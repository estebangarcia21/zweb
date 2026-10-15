# Development

The source build contains runtime `root`, DOM testkit `zwebtestkit`, and sbt plugin `sbtzweb`.
Use JDK 21 and Scala 3.8.4. Runtime dependencies are ZIO 2.1.26, ZIO HTTP 3.11.6 and ScalaTags 0.13.1.
The plugin uses sbt's Scala 2.12 and keeps its tool implementation independent of the runtime.

Run `sbt scalafmtAll test`, `sbt publishLocal`, and `sbt sbtzweb/scripted` in sequence. Do not run two
sbt processes that compile this source build simultaneously. Local publication supports scripted
verification; `sbt publish` writes only to local `target/distribution/maven`; no remote registry is configured.

Development properties are `zweb.development`, `zweb.assetDirectory`, `zweb.liveReload`, and
`zweb.reloadStampFile`. Environment alternatives start with `ZWEB_`. The plugin supplies properties
for `zweb dev`; production has stable asset URLs and no live reload endpoint. Add
`Zweb.liveReloadResponse(request)` to the application's route assembly when supporting hot refresh.
Set `zweb.development.auto-refresh = false` to disable automatic browser refresh.

Commit and push library changes, then update the same full Git commit SHA in each application's
`build.sbt` and `project/plugins.sbt`. Applications compile the runtime, testkit and plugin from
that immutable source revision. A sibling checkout and `publishLocal` are not required.

Hot refresh serves `/_zweb/live-reload.js` as an external same-origin script with no-store caching.
This works with `script-src 'self'`; applications must not loosen their security policy to allow
inline scripts. Both the script and version endpoints are absent outside hot development mode.
