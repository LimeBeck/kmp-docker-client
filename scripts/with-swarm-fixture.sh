#!/usr/bin/env bash
# Enable only a disposable Docker-in-Docker manager; never initialize the host swarm.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
if [[ "${1:-}" != "--inside" ]]; then
  exec "$root/scripts/with-isolated-docker.sh" bash "$0" --inside "$@"
fi
shift
[[ "$RC_DOCKER_SOCKET" =~ ^/tmp/kmp-rc\.[a-zA-Z0-9]+/docker\.sock$ ]]
[[ "$RC_DOCKER_CONTAINER" == kmp-rc-* ]]
[[ "$(docker inspect --format '{{index .Config.Labels "dev.limebeck.rc-acceptance"}}' "$RC_DOCKER_CONTAINER")" == true ]]
docker exec "$RC_DOCKER_CONTAINER" docker --host unix:///rc-run/docker.sock swarm init --advertise-addr eth0 >/dev/null
worker_name="${RC_DOCKER_CONTAINER}-worker"
worker_id=""
cleanup_worker() {
  if [[ -n "$worker_id" ]]; then docker rm -fv "$worker_id" >/dev/null 2>&1 || true; fi
  rm -f "${RC_DOCKER_SOCKET%/*}/worker.sock"
}
trap cleanup_worker EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
worker_id=$(docker run -d --privileged --name "$worker_name" --label dev.limebeck.rc-acceptance=true \
  -v "${RC_DOCKER_SOCKET%/*}:/rc-run" "${RC_DOCKER_IMAGE:-docker:29.0.0-dind}" \
  dockerd --host=unix:///rc-run/worker.sock --tls=false)
for attempt in {1..90}; do
  if docker exec "$worker_name" docker -H unix:///rc-run/worker.sock info >/dev/null 2>&1; then break; fi
  if (( attempt == 90 )); then docker logs "$worker_name"; exit 1; fi
  sleep 1
done
docker exec "$worker_name" chmod 666 /rc-run/worker.sock
"$@" "-PswarmFixtureSocket=$RC_DOCKER_SOCKET"
