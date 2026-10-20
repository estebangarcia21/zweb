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
`auto-refresh` controls the browser client and reload endpoints in both `zweb dev` and
`zweb dev hot`. Set it to `false` to refresh manually while retaining compilation and
restart/hot-swap behavior. Production launches never inject the client.

`development.port` is supplied to the child process and used by `Zweb.serve(routes)`.
An explicit `Zweb.serve(routes, port)` overrides it. It never authorizes terminating another
process listening on that port. See [development](development.md) for watch behavior, hot-swap
limitations, runtime environment alternatives, and startup failure handling.

All configured paths must resolve inside the consuming project. This keeps commands scoped to the
repository where sbt invoked them. Use project-relative paths so configuration remains portable.

Inspect the resolved configuration with:

```sh
sbt "zweb doctor"
```

The plugin settings `zwebConfigFile` and `zwebAddLibrary` support unusual sbt builds. Most
applications should leave them at their defaults.
