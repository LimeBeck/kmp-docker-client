#!/usr/bin/env bash
# Invoke through with-isolated-docker.sh after building the debug dashboard binary.
set -euo pipefail
: "${RC_DOCKER_SOCKET:?Run through with-isolated-docker.sh}"
: "${RC_DOCKER_CONTAINER:?Run through with-isolated-docker.sh}"
fixture_image=busybox:1.37.0
if ! docker image inspect "$fixture_image" >/dev/null 2>&1; then docker pull "$fixture_image"; fi
docker image save "$fixture_image" | docker exec -i "$RC_DOCKER_CONTAINER" docker -H unix:///rc-run/docker.sock load >/dev/null
report_dir=sample/htmxDashboard/build/acceptance
mkdir -p "$report_dir"
port=$(python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    print(sock.getsockname()[1])
PY
)
export RC_DASHBOARD_URL="http://127.0.0.1:$port"
./sample/htmxDashboard/build/bin/linuxX64/debugExecutable/htmxDashboard.kexe "$RC_DOCKER_SOCKET" "$port" > "$report_dir/dashboard.log" 2>&1 &
export RC_DASHBOARD_PID=$!
cleanup() {
    echo "Stopping acceptance dashboard (pid=$RC_DASHBOARD_PID)"
    kill "$RC_DASHBOARD_PID" 2>/dev/null || true
    for attempt in {1..50}; do
        if ! kill -0 "$RC_DASHBOARD_PID" 2>/dev/null; then break; fi
        sleep 0.2
    done
    if kill -0 "$RC_DASHBOARD_PID" 2>/dev/null; then
        echo "Dashboard did not exit within 10s; terminating the owned test process"
        kill -KILL "$RC_DASHBOARD_PID" 2>/dev/null || true
    fi
    wait "$RC_DASHBOARD_PID" 2>/dev/null || true
}
trap cleanup EXIT
for attempt in {1..30}; do
    kill -0 "$RC_DASHBOARD_PID"
    if curl --fail --silent "$RC_DASHBOARD_URL/containers" >/dev/null; then break; fi
    if (( attempt == 30 )); then cat "$report_dir/dashboard.log"; exit 1; fi
    sleep 1
done
python3 -u scripts/dashboard-acceptance.py | tee "$report_dir/results.log"
