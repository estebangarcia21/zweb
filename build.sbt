ThisBuild / organization := "io.estebangarcia21.z"

// Distribute private Maven packages on disk; no remote registry is configured.
ThisBuild / publishTo := Some(
  Resolver.file("private-distribution", file("target/distribution/maven"))(
    Resolver.mavenStylePatterns
  )
)

ThisBuild / publishMavenStyle := true
ThisBuild / version := "0.1.0"
val zioVersion = "2.1.26"

lazy val root: Project = project
  .in(file("."))
  .aggregate(zwebtestkit, sbtzweb)
  .settings(
    name := "zweb",
    scalaVersion := "3.8.4",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % "3.11.6",
      "com.lihaoyi" %% "scalatags" % "0.13.1",
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test
    )
  )

lazy val zwebtestkit = project
  .in(file("zwebtestkit"))
  .dependsOn(LocalProject("root"))
  .settings(
    name := "zwebtestkit",
    scalaVersion := "3.8.4",
    libraryDependencies ++= Seq(
      "org.jsoup" % "jsoup" % "1.23.2",
      "org.scalameta" %% "munit" % "1.3.4",
      "dev.zio" %% "zio-test" % zioVersion
    )
  )

lazy val sbtzweb = project
  .in(file("sbtzweb"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-zweb",
    sbtPlugin := true,
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}"),
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.9",
      "org.scalameta" %% "munit" % "1.3.4" % Test,
      Defaults.sbtPluginExtra(
        "com.indoorvivants" % "sbt-revolver" % "0.11.2",
        (pluginCrossBuild / sbtBinaryVersion).value,
        (update / scalaBinaryVersion).value
      )
    ),
    Compile / resourceGenerators += Def.task {
      val output = (Compile / resourceManaged).value / "zweb-version.properties";
      IO.write(output, s"version=${version.value}\n"); Seq(output)
    }.taskValue
  )
