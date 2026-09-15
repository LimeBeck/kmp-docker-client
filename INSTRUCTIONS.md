# IPC Spec Protocol for kmp-docker-client {#root}

This repository uses spec files as an **inter-process communication layer** between human and AI sessions.

## 1. Addressability {#addressability}

Each normative statement must be addressable via URI:

`spec://io.github.limebeck.kmp-docker-client/<DOC>#<anchor>[.<subanchor>]`

### 1.1 Document map {#addressability.docs}
- `BOOT.md` — session entrypoint.
- `INSTRUCTIONS.md` — protocol and invariants.
- `WAL.md` — continuation state for next session.
- `CLAUDE.md` — cross-tool redirect to BOOT/protocol.
- `AGENTS.md` — condensed operational rules for coding agents.
- `specs/ipc/*.md` — primary project behavior specs and roadmap.

### 1.2 Anchor policy {#addressability.anchors}
- All headings that contain rules should include explicit anchors.
- Use hierarchical anchors with dots when useful (example: `#wal.update.triggers`).
- In commit/PR/test notes prefer direct URI references over natural-language pointers.

## 2. Atomicity {#atomicity}

### 2.1 Change unit {#atomicity.unit}
One atomic change-set should implement one intent:
- one spec point (or tightly coupled points),
- corresponding code/tests updates,
- WAL refresh.

### 2.2 Commit rules {#atomicity.commits}
- Use Conventional Commits.
- Keep title short and informative.
- Body should include why and what changed; include relevant `spec://...` links.
- Do not include markers that disclose AI co-authorship unless explicitly requested by human.

## 3. Conflict protocol {#conflicts}

When AI disagrees with a spec:
1. Implement current spec as written.
2. Add a `REVIEW` marker with rationale.
3. Report it in session output and WAL `Decisions Pending`.

Example marker:
`<!-- REVIEW(spec://io.github.limebeck.kmp-docker-client/INSTRUCTIONS.md#conflicts): propose alternative because ... -->`

## 4. Visibility & cache invalidation {#visibility}

### 4.1 Session start {#visibility.start}
Always read `WAL.md` first.

### 4.2 Session end {#visibility.end}
If meaningful changes happened, update `WAL.md` before finishing.

### 4.3 Human/AI signal channels {#visibility.signals}
- Git diff — primary delta signal.
- WAL — continuation signal.
- REVIEW markers — decision-request signal.

## 5. WAL protocol {#wal}

### 5.1 Required sections {#wal.sections}
`WAL.md` must contain these sections:
- `Current Focus`
- `Completed in Last Session`
- `Next Steps`
- `Known Risks / Constraints`
- `Decisions Pending`
- `Resume Commands`

### 5.2 Update triggers {#wal.update.triggers}
Update WAL:
1. At end of each non-trivial session.
2. Before large/dangerous refactor.
3. Before switching to another module/task.

### 5.3 Brevity budget {#wal.budget}
Keep WAL concise; collapse already-stable history into short complete items.

### 5.4 Release-facing documentation {#wal.release-docs}
The owner requires documentation changes to describe the new version as current when merged. Use the target version in examples and describe implemented features in the present tense; omit temporary upcoming/unreleased/source-only/publication-pending wording from current user guides. Keep actual publication status in WAL and release evidence, and retain historical release records. This documentation convention does not authorize publishing or tagging.

## 6. Project adaptation notes (kmp-docker-client) {#project}

### 6.1 Scope priorities {#project.scope}
Typical high-value areas in this repo:
- Docker API surface (`lib/src/commonMain/.../api/*`)
- model compatibility and serialization
- multiplatform socket implementations (`linux/jvm/js`)
- sample apps behavior consistency

### 6.2 Suggested URI usage in code/tests {#project.uris.in.code}
When adding comments/tests tied to behavior, reference a protocol URI, e.g.:
`Implements: spec://io.github.limebeck.kmp-docker-client/INSTRUCTIONS.md#atomicity.unit`

### 6.3 Non-goals {#project.nongoals}
- Do not bloat BOOT with deep details.
- Do not treat WAL as long-term changelog (Git already stores history).
