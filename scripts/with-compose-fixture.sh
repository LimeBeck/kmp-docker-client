#!/usr/bin/env bash
# Usage: scripts/with-compose-fixture.sh ./gradlew :lib:jvmTest --tests '*Compose*' ...
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
project="kmp-compose-test-$(date +%s)-$$"
# Match the default SDK endpoint regardless of the caller's Docker context.
docker_cmd=(docker --host unix:///var/run/docker.sock)
compose_cmd=("${docker_cmd[@]}" compose -p "$project" -f "$root/lib/tests/compose/compose.yaml")
extra_ids=()
cleanup() {
    local status=$?
    trap - EXIT
    local cleanup_status=0
    for id in "${extra_ids[@]}"; do
        "${docker_cmd[@]}" rm -f "$id" >/dev/null || cleanup_status=1
    done
    "${compose_cmd[@]}" down --timeout 2 --remove-orphans --volumes >/dev/null || cleanup_status=1
    if (( status == 0 )); then status=$cleanup_status; fi
    exit "$status"
}
"${compose_cmd[@]}" version
trap cleanup EXIT
"${compose_cmd[@]}" up -d --scale worker=2 --wait worker
"${compose_cmd[@]}" up -d control
"${compose_cmd[@]}" run --name "$project-oneoff" worker sh -c 'echo oneoff-out; echo oneoff-err >&2'
"${compose_cmd[@]}" up -d stopped
stopped_id=$("${compose_cmd[@]}" ps --all -q stopped)
"${docker_cmd[@]}" wait "$stopped_id" >/dev/null
extra_ids+=("$("${docker_cmd[@]}" create --name "$project-unrelated" alpine:3.21 true)")
extra_ids+=("$("${docker_cmd[@]}" create --name "$project-incomplete" --label "com.docker.compose.project=$project" alpine:3.21 true)")
"$@" "-PcomposeFixtureProject=$project"
