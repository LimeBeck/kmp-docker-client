# PROP-002: CI/CD Release Pipeline Contract {#root}

Status: ACTIVE  
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md`

## Goal {#goal}
Define mandatory behavior for GitHub Actions CI/CD pipeline in this repository.

## Workflow file contract {#workflow.file}
- Release CI workflow must be declared in `.github/workflows/main.yml`.
- PR CI workflow must be declared in `.github/workflows/pr.yml` and build/test without publication.
- Docs CI workflow must be declared in `.github/workflows/docs.yml`.
- Workflows must remain separated by trigger intent:
  - release workflow: library build/test/publish
  - docs workflow: Dokka build/deploy

## Trigger contract {#triggers}
- Release workflow trigger: `push.tags` with pattern `v*`.
- PR workflow trigger: `pull_request` targeting `master`; never `pull_request_target`. PR checks use read-only repository permissions and no release credentials.
- Docs workflow trigger: `push.branches` for `master`.
- Workflows may include `workflow_dispatch` for manual execution.
- Library publish steps must not run for non-tag refs.

## Build and test contract {#ci.jobs}
Gradle build, test, and Dokka checks must use `--warning-mode=fail` to reject deprecated Gradle behavior. Kotlin compilation in all modules treats warnings as errors; generator diagnostics are tracked separately, not blanket-suppressed.
Pipeline must include these sequential jobs:
1. `build` running `./gradlew build`.
2. `test` running `./gradlew :lib:allTests` and depending on `build`.

Test artifacts:
- XML unit test reports from `lib/build/test-results/**/*.xml` must be uploaded even when tests fail (`if: always()`).
- Both `build` and `test` jobs must upload their reports with distinct artifact names (`unit-test-results-build` and `unit-test-results-test`).
- Report publication must run after both jobs reach a terminal state, including when `test` is skipped because `build` failed, and download only `unit-test-results-*` artifacts.

### Dashboard binary artifact {#ci.dashboard-artifact}
- Release CI build and the PR CI Docker 28.5.2/JDK 21 cell explicitly link the dashboard release executable with :sample:htmxDashboard:linkReleaseExecutableLinuxX64.
- Upload sample/htmxDashboard/build/bin/linuxX64/releaseExecutable/htmxDashboard.kexe as the htmx-dashboard-linux-x64 workflow artifact; a missing file is an error.
- The artifact targets Linux X64. Document restoring executable permission after extraction. This is workflow artifact publication, not Maven publication or a GitHub Release asset.
- Binary packaging does not add dashboard UI acceptance to SDK release gates.

### Pull-request validation {#ci.pr}
- PR CI runs build (including all platform tests) and Dokka with --warning-mode=fail and at most two Gradle workers.
- PR CI uses ubuntu-24.04 with the Cartesian matrix Docker 28.5.2/29.0.0 × Temurin JDK 17/21. Every cell builds/tests JVM, Node.js and Linux X64 against the same explicit API 1.51 URL prefix. Test report artifact names include Docker and JDK versions; fail-fast is disabled.
- Kotlin/JS uses pinned Node.js 24.16.0 from gradle.properties via NodeJsEnvSpec; no deprecated runtime configuration API. Release CI retains Docker 28.5.2 and JDK 21.
- Superseded PR runs may be cancelled. Release jobs are not triggered by PRs.
- Tests synchronize with observable completion instead of sleeps. Repeated-session tests retain finite per-session and overall deadlines without using one short-session deadline for the entire series.

### SDK integration acceptance {#ci.acceptance}
- PR CI's Docker 29/JDK 21 cell runs the Kotlin/JVM isolated daemon restart/resubscription test after build. Release CI runs it in the test job before publication.
- The SDK restart test uses scripts/with-isolated-docker.sh, which owns a labelled disposable daemon, socket and data volume; the user's/main runner daemon must not be restarted by acceptance tests.
- The normal JVM test task excludes the opt-in restart test. Only daemonRestartTest with harness-provided fixture identity may restart the temporary daemon.
- Upload SDK JUnit reports with always(), including on failure.
- Dashboard HTTP/WebSocket, browser and UI acceptance are not SDK PR or release gates. Optional sample smoke checks are independent of library publication.

### Public API compatibility {#ci.abi}
- The library enables Kotlin Gradle Plugin ABI validation, including JVM and KLib outputs for JS/Linux X64. Generated models are included without exclusions.
- Commit the initial reference dumps for the candidate; `checkKotlinAbi` must run with build/check and explicitly in PR CI. Unsupported targets must fail rather than infer ABI from another target.
- CI never runs `updateKotlinAbi`. Intentional API changes require reviewing the dump diff and updating migration documentation before refreshing the baseline locally.
- ABI snapshots detect declaration changes, not behavioral, wire-format or all source-compatibility changes. Integration tests and release review remain required.

## Publish contract {#publish}
- Publishing is Maven Central-oriented and must depend on successful `test` job.
- Publish job must use `:lib:publishAndReleaseToMavenCentral`.
- Library version must be derived from release tag:
  - expected tag format: `v<semver>`
  - effective Gradle property: `-PlibVersion=<semver-without-v>`
- Required publish inputs:
  - GPG material: `GPG_SIGNING_KEY`, `SECRET_PASSPHRASE`, `GPG_PASSWORD`, `GPG_KEY_ID`
  - Maven Central credentials: `OSSRH_USERNAME`, `OSSRH_PASSWORD`

### Development version {#publish.development}
- The default `libVersion` in `gradle.properties` is `1.0.1`, the next release target.
- Release tags continue to override this default through `-PlibVersion`; changing the default does not publish a release.

## Test results publication contract {#test-results}
- Pipeline must include a dedicated post-test results publication job.
- It should download CI artifacts and publish JUnit-style reports to GitHub checks UI.
- Test-results job must have GitHub token permission `checks: write` (or equivalent) to create check runs.

## Dokka docs publication contract {#docs}
- Handwritten public client and domain methods carry KDoc describing parameters, returned values, failure timing, and resource ownership where relevant; generated API pages must include these descriptions.
- Pipeline must include a GitHub Pages deployment job for Dokka HTML docs.
- Docs job must run `:lib:dokkaGenerateHtml`.
- Include docs/USAGE.md as the Dokka module guide, linking workflows to generated API documentation. Marked Kotlin examples are extracted into commonTest sources and compiled; Dokka generation depends on JVM test-source compilation without running Docker tests.
- Published artifact path must match Dokka output directory (`lib/build/dokka/html`).
- Docs deployment must be isolated in docs workflow (not combined with release workflow).
- Deployment must use GitHub Pages actions:
  - `actions/configure-pages@v6`
  - `actions/upload-pages-artifact@v5`
  - `actions/deploy-pages@v5`
- Required job permissions:
  - `contents: read` for repository checkout
  - `pages: write`
  - `id-token: write`

## Cache/runtime baseline {#runtime}
- JavaScript actions (including nested composite dependencies) must use Node.js 24 rather than deprecated Node.js 20.
- Runner baseline: `ubuntu-latest`.
- Java baseline: Temurin JDK 21.
- Release build/test jobs must provision Docker 28.5.2 (API 1.51), expose its socket at `/var/run/docker.sock`, and verify `/v1.51/_ping` before Gradle. The runner-provided daemon version is not a supported implicit dependency.
- Cache should include Gradle and Kotlin/Native directories used by project builds.
- Cache keys must include `gradle/libs.versions.toml` so dependency updates invalidate the cache.

## Change control {#change-control}
- Any CI/CD modification must reference this spec:
  - `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-002.md#root`
- If release strategy changes (for example, adding PR trigger or changing publish target), update corresponding anchors first.

## Changelog {#changelog}
- 2026-09-13: owner assigned AutoCloseable, KDoc and terminal scrollbar improvements to 1.0.1.
- 2026-09-13: owner requested stable 1.0.0 preparation and a Dokka-integrated usage guide with compiled examples.
- 2026-09-13: owner removed dashboard acceptance from SDK release scope; retained Kotlin SDK integration and isolated recovery checks.
- 2026-09-12: required isolated daemon recovery and real dashboard application acceptance before publication, with failure logs retained.
- 2026-09-12: established explicit Docker/JDK PR matrix, pinned Kotlin/JS runtime and mandatory all-target ABI snapshots for candidate readiness.
- 2026-09-12: added isolated PR validation and selected 1.0.0-rc as the next development target.
- 2026-09-12: migrated JavaScript action runtimes to Node.js 24 and made Gradle deprecations fail CI checks.
- 2026-09-11: pinned a compatible Docker daemon after v0.0.9 CI rejected API 1.51 on a runner supporting only 1.48.
- 2026-09-11: required JUnit artifacts from both build/test jobs and publication after an upstream failure.
- 2026-03-07: initial CI/CD release pipeline contract added based on `.github/workflows/main.yml`.
- 2026-03-07: added Dokka GitHub Pages publication contract.
- 2026-03-07: split CI/CD into separate release/docs workflows.
- 2026-03-07: release version source fixed to Git tag (`v*` -> `libVersion`).
- 2026-03-08: required `checks: write` permission for unit test result publication.
- 2026-09-10: added docs checkout permission and version catalog cache invalidation.
