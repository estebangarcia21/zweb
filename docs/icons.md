# Icons

Icons belong to the consuming application, not to the Zweb runtime. This keeps the library small
and prevents one application's icon choices from becoming global API.

Configure `zweb.scala-package`, then run icon commands through sbt:

```sh
sbt "zweb icon add circle-dollar-sign"
sbt "zweb icon list"
sbt "zweb icon remove circle-dollar-sign"
sbt "zweb icon sync"
```

`add` downloads the pinned Lucide SVG, removes fixed dimensions, marks it decorative, changes the
class to `icon`, and regenerates an application-owned `Icons.scala`. A name such as
`circle-dollar-sign` becomes `Icons.circleDollarSign`.

By default the generated source is:

```text
src/main/scala/<scala-package-as-directories>/Icons.scala
```

Set `zweb.icons-output` to place it elsewhere. Zweb owns the complete generated file; do not
add hand-written methods to it. Put custom icons in another object.

Zweb records deterministic inputs in:

```text
.zweb/icons.lock
.zweb/icons/<name>.svg
```

Commit both the generated Scala source and `.zweb/`. `sync` validates the cache and restores a
missing cached SVG from the exact locked Lucide version, rejecting changed content by SHA-256.

Generated icons are decorative and inherit `currentColor`. Pair them with visible text or give their
containing control an accessible name. Review Lucide's license and distribute its required notice
with the consuming application.
