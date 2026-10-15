# Getting started

Zweb is consumed from `https://github.com/estebangarcia21/zweb.git` at a full commit SHA.
Choose a published commit and replace `COMMIT_SHA` below with that same revision in both files.

In `project/plugins.sbt`:

```scala
lazy val zwebPlugin = ProjectRef(
  uri("https://github.com/estebangarcia21/zweb.git#COMMIT_SHA"), "sbtzweb"
)
lazy val root = project.in(file(".")).dependsOn(zwebPlugin)
```

In `build.sbt`:

```scala
lazy val zwebRepository = uri("https://github.com/estebangarcia21/zweb.git#COMMIT_SHA")
lazy val zweb = ProjectRef(zwebRepository, "root")
lazy val zwebtestkit = ProjectRef(zwebRepository, "zwebtestkit")
lazy val root = project.in(file("."))
  .enablePlugins(ZwebPlugin)
  .dependsOn(zweb, zwebtestkit % "test->compile")
  .settings(scalaVersion := "3.8.4", zwebAddLibrary := false)
```

`zwebAddLibrary := false` prevents the plugin from adding Maven runtime/testkit dependencies.
Git and network access are needed on the first build; sbt caches the source checkout. No sibling
checkout, vendored Maven repository, or local publication is needed. Release bundles must include
the source runtime's `Compile / packageBin`, as it is not an external dependency JAR. Assembly
includes source project dependencies automatically. Testkit belongs only on the test classpath.

```scala
import io.estebangarcia21.z.web.*
import scalatags.Text.all.*
import zio.*
import zio.http.*

object Main extends ZIOAppDefault:
  private val view = page[Unit](_ => PageMeta("Hello"))(_ => div(h1("Hello")))
  def run = Zweb.serve(Routes(Method.GET / "" -> handler(html(view(())))))
```

Enable `ZwebPlugin` in a consuming build. Configure `zweb.conf` with an application Scala package
and asset directory. The `zweb init`, `zweb doctor`, `zweb dev`, `zweb icon`, and `zweb js` commands
manage configuration, development refresh, local SVG icons, and pinned JavaScript assets. The
application owns authentication, static asset serving and business logic.
