# JavaScript dependencies

Zweb can pin browser-ready npm assets into the consuming application. Applications serve these
files locally and have no CDN dependency at runtime.

Run commands through the consumer's sbt build:

```sh
sbt "zweb js add htmx"
sbt "zweb js add htmx 2.0.10"
sbt "zweb js list"
sbt "zweb js sync"
sbt "zweb js remove htmx"
```

`add` without a version resolves jsDelivr's current `latest` tag once and writes the resulting exact
version to the lock file. Supplying a version must use an exact semantic version; ranges and tags are
not accepted. Re-running `add` updates that dependency. `sync` downloads the locked files and rejects
content whose checksum differs from the lock file.

The aliases `htmx`, `htmx-head-support`, `alpine`, and `posthog-js` select their browser distributions
and stable local filenames. Other lowercase npm package names are supported when their
`package.json` declares a JavaScript `jsdelivr` or `unpkg` entry.

Files and `jsDeps.lock` live under `<assets-directory>/js/vendor/`; the default assets directory is
`src/main/resources/assets`. Commit the lock and downloaded files. `sync` fetches every exact locked
version and rejects content whose SHA-256 no longer matches.

These commands need network access only while adding or syncing. Review each package's license and
store required notices beside the vendored dependency; Zweb does not make licensing decisions.
