# Configuration

Zweb reads one optional HOCON file named `zweb.conf` from the consuming sbt project's root.
Generate the usual starting point with:

```sh
sbt "zweb init com.example.myapp"
```

The complete configuration surface is:

```hocon
zweb {
  scala-package = "com.example.myapp"
  icons-output = "src/main/scala/com/example/myapp/Icons.scala"
  assets-directory = "src/main/resources/assets"

  development {
    port = 8080
    auto-refresh = true
  }
}
```

`scala-package` is required only for icon generation. When it is present, `icons-output` defaults to
an `Icons.scala` in that package and normally should be omitted. `assets-directory` and
`development.port` and `development.auto-refresh` have the values shown above by default.
`auto-refresh` controls the browser polling script and reload endpoint in `zweb dev hot` only.
Set it to `false` to keep class hot swapping while refreshing the browser manually. Restart the hot
watch after changing this setting. Ordinary `zweb dev` and production launches never inject the
polling script.

All configured paths must resolve inside the consuming project. This keeps commands scoped to the
repository where sbt invoked them. Use project-relative paths so configuration remains portable.

Inspect the resolved configuration with:

```sh
sbt "zweb doctor"
```

The plugin settings `zwebConfigFile` and `zwebAddLibrary` support unusual sbt builds. Most
applications should leave them at their defaults.
