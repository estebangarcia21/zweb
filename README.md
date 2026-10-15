# zweb

Scala 3 foundation for server-rendered ZIO HTTP applications. Contains `zweb`, `zwebtestkit`, and `sbt-zweb`. Derived from Paperweb rendering and tooling; Paperweb remains independent.

Run `sbt test`, `sbt publishLocal sbtzweb/scripted`, and `sbt publish` with JDK 21.
Applications consume source from https://github.com/estebangarcia21/zweb pinned to an immutable
commit; see [getting started](docs/getting-started.md). Optional Maven publication writes only to
`target/distribution/maven`; no remote registry is configured. The plugin selects matching runtime/testkit versions. Commands use `zweb.conf` and `.zweb/`: `zweb init`, `zweb dev`, `zweb doctor`, `zweb icon`, and `zweb js`.

HTML views are pure ScalaTags functions. Use `io.estebangarcia21.z.web.html` to encode them and ZIO HTTP to assemble routes. Production resources belong to ZIO scopes. Development assets and live reload remain explicitly opt-in.
