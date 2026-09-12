#!/usr/bin/env bash
# Run acceptance commands against a disposable daemon, never the host daemon.
set -euo pipefail
if (( $# == 0 )); then echo "Usage: $0 command [args...]" >&2; exit 2; fi
socket_dir=$(mktemp -d /tmp/kmp-rc.XXXXXX)
container_name="kmp-rc-${socket_dir##*.}"
container_id=""
cleanup() {
  if [[ -n "$container_id" ]]; then docker rm -fv "$container_id" >/dev/null 2>&1 || true; fi
  rm -f "$socket_dir/docker.sock"
  rmdir "$socket_dir" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
container_id=$(docker run -d --privileged --name "$container_name" \
  --label dev.limebeck.rc-acceptance=true \
  -v "$socket_dir:/rc-run" docker:29.0.0-dind \
  dockerd --host=unix:///rc-run/docker.sock --tls=false)
export RC_DOCKER_SOCKET="$socket_dir/docker.sock"
export RC_DOCKER_CONTAINER="$container_name"
for attempt in {1..90}; do
  if docker exec "$container_name" docker -H unix:///rc-run/docker.sock info >/dev/null 2>&1; then break; fi
  if (( attempt == 90 )); then docker logs "$container_name"; exit 1; fi
  sleep 1
done
# Only the temporary socket is accessible to the unprivileged acceptance process.
docker exec "$container_name" chmod 666 /rc-run/docker.sock
# Reuse the small local fixture image when available; otherwise pull inside the daemon.
if docker image inspect alpine:latest >/dev/null 2>&1; then
  docker image save alpine:latest | docker exec -i "$container_name" docker -H unix:///rc-run/docker.sock load >/dev/null
else
  docker exec "$container_name" docker -H unix:///rc-run/docker.sock pull alpine:latest
fi
"$@"
