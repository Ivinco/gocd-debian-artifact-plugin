# gocd-debian-artifact-plugin

A GoCD **Artifact Extension** plugin (API v2.0) that publishes `.deb` files
a job built to a hosted Debian repository on
[ArtifactKeeper](https://github.com/artifact-keeper/artifact-keeper), and
can fetch them back down in a downstream job.

No existing GoCD plugin does this: `gocd-contrib/deb-repo-poller` is a
*Package Material* plugin (polls a repo to trigger builds — the opposite
direction), and `eaiesb/Gocd-Artifactory-Plugin` talks JFrog Artifactory's
proprietary client API (won't speak to ArtifactKeeper's protocol) and is
unmaintained since 2017 besides.

**Not installed anywhere.** This is the plugin jar only — installing it on
a production GoCD server is a separate, deliberate step (a new jar under a
server other pipelines depend on, requires a server restart) that needs its
own go-ahead.

## What it talks to

Plain HTTP against ArtifactKeeper's native Debian protocol — there is no
ArtifactKeeper SDK, this is the same `PUT /debian/{repo}/pool/...` contract
[ArtifactKeeper's own e2e tests](https://github.com/artifact-keeper/artifact-keeper/blob/main/scripts/e2e-syspkg/test-debian.sh)
use:

- **Publish**: `PUT {RegistryUrl}/debian/{RepositoryKey}/pool/{Component}/{poolLetter}/{packageName}/{filename}`, HTTP Basic auth, `Content-Type: application/vnd.debian.binary-package`.
- **Fetch**: plain `GET` of the same URL, same Basic auth.
- Pool letter follows the standard dpkg convention: first letter of the
  package name, except `lib*` packages use the first four letters (e.g.
  `libssl1.1` → `libs`) — see `DebianPoolPath`.

## Configuration

**Artifact Store** (one per ArtifactKeeper repo, shared by every pipeline
that publishes into it):

| Field | Required | Secure | Example |
|---|---|---|---|
| `RegistryUrl` | yes | no | `https://artifactkeeper.example.com` |
| `RepositoryKey` | yes | no | `internal` |
| `Username` | yes | no | `svc-gocd-publisher` |
| `Password` | yes | yes | an ArtifactKeeper service-account API token with `write:artifacts` scope, ideally restricted to this one repository |

**Publish config** (per job `artifacts: external:` block):

| Field | Required | Example |
|---|---|---|
| `Pattern` | yes | `*.deb` — glob, relative to the job's working directory |
| `Component` | no (default `main`) | `main` |

**Fetch config** (per downstream `Fetch Artifact` task, if used):

| Field | Required | Example |
|---|---|---|
| `DestinationOnAgent` | no (default: working directory) | `downloaded` |

## Example pipeline usage

Once an artifact store exists (GoCD UI: **Admin → Artifact Stores → Add**,
plugin "ArtifactKeeper Debian Artifact Plugin", id e.g. `artifactkeeper-debian`),
a job publishes its built package with:

```yaml
artifacts:
  external:
    id: my-package
    store_id: artifactkeeper-debian
    configuration:
      Pattern: "*.deb"
      Component: main
```

No shell script calling `curl`/`scp` — GoCD calls the plugin directly after
the job succeeds, the same way it calls any other Artifact Extension
plugin (e.g. the official Docker Registry one) for a pluggable artifact.

## Building

```bash
mvn clean package
```

Produces `target/gocd-debian-artifact-plugin-1.0.0.jar` (~345 KB, gson
shaded/relocated in since GoCD loads one plugin per jar with no shared
classpath; `go-plugin-api` itself is `provided` — the host JVM supplies it).

## Installing on a GoCD server

```bash
cp target/gocd-debian-artifact-plugin-1.0.0.jar /var/lib/go-server/plugins/external/
systemctl restart go-server   # plugins load at server startup only
```

Then **Admin → Artifact Stores** should list "ArtifactKeeper Debian Artifact
Plugin" as an installable store type.

## Tests

```bash
mvn test
```

17 tests: pure unit tests for the pool-letter/package-name logic
(`DebianPoolPathTest`), HTTP-contract tests against a `MockWebServer`
instance verifying the exact request GoCD would trigger
(`ArtifactKeeperClientTest`), and full request/response wiring tests for
every plugin message including an end-to-end `publish-artifact` call
(`DebianArtifactPluginTest`). No live ArtifactKeeper instance or GoCD server
needed to run them.

## Known gaps / things to verify before relying on this in production

- **Not tested against a real GoCD server** — only against the documented
  [Artifact plugin contract](https://plugin-api.gocd.org/current/artifacts/)
  and the go-plugin-api classes it's built against. The message shapes and
  constructor signatures were checked against the actual
  `go-plugin-api-25.2.0` jar, but end-to-end behavior on a live server
  (icon rendering, config form rendering, actual publish from a real job)
  has not been observed.
- `cd.go.artifact.fetch.validate` — the published extension docs literally
  reuse the string `cd.go.artifact.publish.validate` in the fetch-config
  section, breaking the otherwise perfectly symmetric
  `<scope>.get-metadata` / `<scope>.get-view` / `<scope>.validate` naming
  used everywhere else. Implemented here as `cd.go.artifact.fetch.validate`
  (the symmetric form) on the assumption that's a documentation typo — flag
  this loudly in the docs and change one constant in
  `DebianArtifactPlugin.java` if a real server proves otherwise.
- Re-publishing the same filename overwrites it silently (whatever
  ArtifactKeeper's own `PUT` semantics are for an existing pool path — not
  independently verified here).
- No retry/backoff on transient network failures — a flaky connection to
  the ArtifactKeeper server fails the job outright.
- `src/main/resources/plugin.xml`'s `<vendor><url>` is a placeholder
  (`github.com/CHANGEME/...`) — point it at wherever this project actually
  ends up living.
