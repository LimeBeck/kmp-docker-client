# PROP-000: Foundational Decisions and Invariants {#root}

Status: ACTIVE  
Module URI: `spec://io.github.limebeck.kmp-docker-client/specs/ipc/PROP-000.md`

## Immutable priorities (read first) {#invariants}
1. Human intent priority: Human > Specs > Tests > Code.
2. Client must remain Kotlin Multiplatform-first (`commonMain` source of truth).
3. Docker API interaction must preserve explicit `Result<Success, ErrorResponse>` contract.
4. Network transport baseline is Unix domain socket.

## Runtime architecture {#runtime}
- Main entrypoint is `DockerClient` with configurable JSON and connection settings.
- API version prefix is fixed at `1.51` unless explicitly revised in spec.
- All built-in HTTP and raw hijack requests must use the `/v1.51/` path prefix through the same path builder.
- HTTP transport uses Ktor CIO client + unix socket capability integration.

## Serialization contract {#serialization}
- JSON parser defaults must tolerate daemon/schema drift:
  - `ignoreUnknownKeys = true`
  - `explicitNulls = false`
  - `coerceInputValues = true`
- Parse behavior:
  - HTTP 2xx => decode as success payload type
  - non-2xx => decode as `ErrorResponse`

## Auth contract {#auth}
- Registry auth shall be cached in `DockerClientConfig.auth`.
- Supported auth modes:
  - credentials (username/password)
  - identity token
- Registry server resolution should try canonical and fallback forms.

### HTTP logging {#auth.logging}
- HTTP logging must not record request or response bodies.
- Registry auth/config and HTTP authorization headers must be masked, including when DEBUG logging is enabled.
- Auth endpoint exchanges must be excluded from HTTP logging.

## Platform support baseline {#platforms}
- JVM
- Linux X64
- NodeJS

## Non-goals (current phase) {#nongoals}
- TCP/TLS daemon connection support in core config.
- Windows named pipe support.
- Non-Docker OCI runtime abstractions.

## Changelog {#changelog}
- 2026-03-07: initial foundational spec created from existing implementation.
