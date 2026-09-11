# PROP-002: CI/CD Release Pipeline Contract {#root}

Status: ACTIVE  
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md`

## Goal {#goal}
Define mandatory behavior for GitHub Actions CI/CD pipeline in this repository.

## Workflow file contract {#workflow.file}
- Release CI workflow must be declared in `.github/workflows/main.yml`.
- Docs CI workflow must be declared in `.github/workflows/docs.yml`.
- Workflows must remain separated by trigger intent:
  - release workflow: library build/test/publish
  - docs workflow: Dokka build/deploy

## Trigger contract {#triggers}
- Release workflow trigger: `push.tags` with pattern `v*`.
- Docs workflow trigger: `push.branches` for `master`.
- Both workflows may include `workflow_dispatch` for manual execution.
- Library publish steps must not run for non-tag refs.

## Build and test contract {#ci.jobs}
Pipeline must include these sequential jobs:
1. `build` running `./gradlew build`.
2. `test` running `./gradlew :lib:allTests` and depending on `build`.

Test artifacts:
- XML unit test reports from `lib/build/test-results/**/*.xml` must be uploaded even when tests fail (`if: always()`).

## Publish contract {#publish}
- Publishing is Maven Central-oriented and must depend on successful `test` job.
- Publish job must use `:lib:publishAndReleaseToMavenCentral`.
- Library version must be derived from release tag:
  - expected tag format: `v<semver>`
  - effective Gradle property: `-PlibVersion=<semver-without-v>`
- Required publish inputs:
  - GPG material: `GPG_SIGNING_KEY`, `SECRET_PASSPHRASE`, `GPG_PASSWORD`, `GPG_KEY_ID`
  - Maven Central credentials: `OSSRH_USERNAME`, `OSSRH_PASSWORD`

## Test results publication contract {#test-results}
- Pipeline must include a dedicated post-test results publication job.
- It should download CI artifacts and publish JUnit-style reports to GitHub checks UI.
- Test-results job must have GitHub token permission `checks: write` (or equivalent) to create check runs.

## Dokka docs publication contract {#docs}
- Pipeline must include a GitHub Pages deployment job for Dokka HTML docs.
- Docs job must run `:lib:dokkaGenerateHtml`.
- Published artifact path must match Dokka output directory (`lib/build/dokka/html`).
- Docs deployment must be isolated in docs workflow (not combined with release workflow).
- Deployment must use GitHub Pages actions:
  - `actions/configure-pages`
  - `actions/upload-pages-artifact`
  - `actions/deploy-pages`
- Required job permissions:
  - `contents: read` for repository checkout
  - `pages: write`
  - `id-token: write`

## Cache/runtime baseline {#runtime}
- Runner baseline: `ubuntu-latest`.
- Java baseline: Temurin JDK 21.
- Cache should include Gradle and Kotlin/Native directories used by project builds.
- Cache keys must include `gradle/libs.versions.toml` so dependency updates invalidate the cache.

## Change control {#change-control}
- Any CI/CD modification must reference this spec:
  - `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#root`
- If release strategy changes (for example, adding PR trigger or changing publish target), update corresponding anchors first.

## Changelog {#changelog}
- 2026-03-07: initial CI/CD release pipeline contract added based on `.github/workflows/main.yml`.
- 2026-03-07: added Dokka GitHub Pages publication contract.
- 2026-03-07: split CI/CD into separate release/docs workflows.
- 2026-03-07: release version source fixed to Git tag (`v*` -> `libVersion`).
- 2026-03-08: required `checks: write` permission for unit test result publication.
