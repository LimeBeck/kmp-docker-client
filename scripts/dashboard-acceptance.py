#!/usr/bin/env python3
"""HTTP/WebSocket application acceptance. Requires the isolated daemon harness.

The browser-only fullscreen/layout checks are recorded separately in RC-ACCEPTANCE.md.
"""
import base64
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import time
import threading
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get('RC_DASHBOARD_URL', 'http://127.0.0.1:18081')
DAEMON = os.environ['RC_DOCKER_CONTAINER']
assert DAEMON.startswith('kmp-rc-')
PREFIX = 'dashboard-rc-' + uuid.uuid4().hex[:10]


def docker(*args):
    return subprocess.check_output(['docker', 'exec', DAEMON, 'docker', '-H', 'unix:///rc-run/docker.sock', *args], text=True, timeout=30).strip()


def inspect(identifier):
    return json.loads(docker('inspect', identifier))[0]


def request(path, data=None, method=None):
    headers = {'HX-Request': 'true'}
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    req = urllib.request.Request(BASE + path, body, headers, method=method)
    with urllib.request.urlopen(req, timeout=90 if path == '/images/pull' else 20) as response:
        return response.read().decode(), response.headers


def action(path, values=None):
    body, headers = request(path, values or {}, 'POST')
    target = headers.get('HX-Redirect')
    assert target, body
    return target.rsplit('/', 1)[-1]


def serving(identifier):
    item = inspect(identifier)
    port = item['NetworkSettings']['Ports']['8080/tcp'][0]['HostPort']
    deadline = time.monotonic() + 10
    while True:
        result = subprocess.run(['docker', 'exec', DAEMON, 'wget', '-qO-', 'http://127.0.0.1:' + port], capture_output=True, text=True, timeout=5)
        if result.returncode == 0:
            return result.stdout
        if time.monotonic() >= deadline:
            raise AssertionError('Service failed readiness: ' + result.stderr)
        time.sleep(0.2)


class WebSocket:
    def __init__(self, path):
        url = urllib.parse.urlparse(BASE)
        self.sock = socket.create_connection((url.hostname, url.port), timeout=5)
        self.file = self.sock.makefile('rb')
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f'GET {path} HTTP/1.1\r\nHost: {url.netloc}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n').encode())
        assert b'101' in self.file.readline()
        headers = {}
        while (line := self.file.readline()) != b'\r\n':
            assert line
            name, value = line.decode().split(':', 1)
            headers[name.lower()] = value.strip()
        expected = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
        assert headers['sec-websocket-accept'] == expected

    def send(self, payload, opcode=2):
        if isinstance(payload, str):
            payload = payload.encode()
        mask = os.urandom(4)
        size = len(payload)
        header = bytes([128 | opcode, 128 | size]) if size < 126 else bytes([128 | opcode, 128 | 126]) + struct.pack('!H', size)
        self.sock.sendall(header + mask + bytes(value ^ mask[index % 4] for index, value in enumerate(payload)))

    def receive_until(self, expected):
        output = b''
        deadline = time.monotonic() + 8
        while expected not in output and time.monotonic() < deadline:
            head = self.file.read(2)
            assert len(head) == 2, output
            opcode, length = head[0] & 15, head[1] & 127
            if length == 126:
                length = struct.unpack('!H', self.file.read(2))[0]
            elif length == 127:
                length = struct.unpack('!Q', self.file.read(8))[0]
            assert length <= 1024 * 1024
            payload = self.file.read(length)
            if opcode == 9:
                self.send(payload, 10)
            elif opcode in (0, 1, 2):
                output += payload
            else:
                assert opcode != 8, output
        assert expected in output, output
        return output

    def close(self):
        self.file.close()
        self.sock.close()


def terminal(path):
    ws = WebSocket(path)
    try:
        ws.send(json.dumps({'type': 'resize', 'rows': 31, 'cols': 97}), 1)
        # Disable echo first: the asserted result must come from the shell, not input echo.
        ws.send("stty -echo; printf '\\101\\103\\113'; stty size\n")
        ws.receive_until(b'ACK31 97')
        ws.send("printf '\\320\\237\\321\\200\\320\\270\\320\\262\\320\\265\\321\\202 \\360\\237\\214\\215'\n")
        ws.receive_until('Привет 🌍'.encode())
        ws.send("printf 'prompt> '\n")
        ws.receive_until(b'prompt> ')
    finally:
        ws.close()


def read_stream(path, ready=None):
    with urllib.request.urlopen(BASE + path, timeout=8) as response:
        if ready is not None:
            ready.set()
        for _ in range(30):
            line = response.readline().decode()
            if line.startswith('data: '):
                return line
    raise AssertionError('No stream data: ' + path)


def main():
    ids = set()
    volume = PREFIX + '-data'
    network = PREFIX + '-network'
    try:
        assert 'Command arguments' in request('/containers')[0]
        assert docker('image', 'inspect', 'busybox:1.37.0'), 'Preload the fixture image with the harness'
        request('/volumes', {'name': volume}, 'POST')
        docker('network', 'create', network)
        values = {
            'name': PREFIX, 'image': 'busybox:1.37.0',
            'cmd': 'sh\n-c\nmkdir -p /data/www; echo "$REVISION" >> /data/history; cp /data/history /data/www/index.html; httpd -p 8080 -h /data/www; while true; do echo alive; echo diagnostic >&2; sleep 1; done',
            'env': 'REVISION=first', 'ports': '127.0.0.1:0:8080/tcp',
            'volumes': volume + ':/data', 'network': network,
        }
        original = action('/containers/create', values)
        ids.add(original)
        info = inspect(original)
        assert 'REVISION=first' in info['Config']['Env']
        assert info['Mounts'][0]['Name'] == volume
        assert network in info['NetworkSettings']['Networks']
        assert serving(original) == 'first\n'
        assert 'Memory:' in read_stream('/containers/' + original + '/stats')
        with urllib.request.urlopen(BASE + '/containers/' + original + '/logs', timeout=8) as response:
            output = ''
            for _ in range(30):
                output += response.readline().decode()
                if 'alive' in output and 'diagnostic' in output:
                    break
            assert 'alive' in output and 'diagnostic' in output and 'text-red-400' in output, output
        exec_id = action('/containers/' + original + '/exec', {'command': ''})
        terminal('/exec/' + exec_id + '/ws')
        interactive = action('/containers/create', {'name': PREFIX + '-tty', 'image': 'alpine:latest', 'cmd': '/bin/sh', 'tty': 'on'})
        ids.add(interactive)
        terminal('/containers/' + interactive + '/terminal/ws')
        print('PASS: create, HTTP readiness, stdout/stderr, stats, exec and attach', flush=True)
        def socket_count():
            count = 0
            for fd in Path('/proc/' + os.environ['RC_DASHBOARD_PID'] + '/fd').iterdir():
                try:
                    count += os.readlink(fd).startswith('socket:')
                except FileNotFoundError:
                    pass
            return count
        def cycle():
            read_stream('/containers/' + original + '/logs')
            read_stream('/containers/' + original + '/stats')
            next_exec = action('/containers/' + original + '/exec', {'command': '/bin/sh'})
            terminal('/exec/' + next_exec + '/ws')
        for _ in range(2):
            cycle()
        def settled_sockets():
            deadline = time.monotonic() + 10
            previous = socket_count()
            stable_since = time.monotonic()
            while time.monotonic() < deadline:
                time.sleep(0.1)
                current = socket_count()
                if current != previous:
                    previous, stable_since = current, time.monotonic()
                if time.monotonic() - stable_since >= 1.2:
                    return current
            raise AssertionError('Application socket count did not settle')
        before = settled_sockets()
        for _ in range(10):
            cycle()
        # Closing an event stream with no events and an idle registry operation must also release resources.
        for index in range(4):
            port = 15000 + index
            slow = docker('run', '-d', '--name', PREFIX + '-slow-' + str(index), '--network', 'host', 'busybox:1.37.0', 'nc', '-lk', '-s', '127.0.0.1', '-p', str(port), '-e', 'sh', '-c', 'sleep 600')
            ids.add(slow)
            docker('exec', slow, 'sh', '-c', f'for i in 1 2 3 4 5; do grep -q ":{port:04X} " /proc/net/tcp /proc/net/tcp6 && exit 0; sleep 1; done; exit 1')
            with urllib.request.urlopen(BASE + '/system/events', timeout=5) as response:
                assert response.readline().startswith(b': keepalive')
            cancel = urllib.request.Request(BASE + '/images/pull', urllib.parse.urlencode({'image-pull-name': f'127.0.0.1:{port}/rc-cancel:latest'}).encode())
            with urllib.request.urlopen(cancel, timeout=5) as response:
                assert json.loads(response.readline())['state'] == 'heartbeat'
        deadline = time.monotonic() + 10
        while socket_count() > before + 2 and time.monotonic() < deadline:
            time.sleep(0.2)
        after = settled_sockets()
        assert after <= before + 2, (before, after)
        print(f'PASS: active/idle stream and pull cancellation cleanup ({before} -> {after} sockets)', flush=True)
        # Consume a real event through the dashboard while mutating only this fixture.
        with concurrent.futures.ThreadPoolExecutor() as pool:
            ready = threading.Event()
            event = pool.submit(read_stream, '/system/events', ready)
            assert ready.wait(5), 'Event stream did not open'
            docker('update', '--restart', 'on-failure', interactive)
            assert 'update' in event.result(timeout=10)
        # The failed prepare path preserves the original and its persistent data.
        failed, _ = request('/containers/' + original + '/recreate', dict(values, image=PREFIX + ':missing'), 'POST')
        assert 'Error:' in failed and inspect(original)['State']['Running']
        assert serving(original) == 'first\n'
        # Start failure after preparation must restore the original too.
        failed, _ = request('/containers/' + original + '/recreate', dict(values, cmd='/missing-command'), 'POST')
        assert 'Error:' in failed and inspect(original)['State']['Running']
        assert 'first' in serving(original)
        print('PASS: prepare/start failure preserves the original', flush=True)
        candidate = action('/containers/' + original + '/recreate', dict(values, env='REVISION=second'))
        ids.add(candidate)
        assert not inspect(original)['State']['Running']
        assert 'second' in serving(candidate)
        assert 'Confirm replacement' in request('/containers/' + candidate)[0]
        assert action('/containers/' + candidate + '/rollback') == original
        assert inspect(original)['State']['Running']
        # A shared volume keeps candidate writes even after rollback, as documented in the UI.
        assert 'second' in serving(original)
        candidate = action('/containers/' + original + '/recreate', dict(values, env='REVISION=third'))
        ids.add(candidate)
        assert 'third' in serving(candidate)
        assert action('/containers/' + candidate + '/confirm') == candidate
        assert original not in docker('ps', '-aq', '--no-trunc').splitlines()
        assert 'Prepare Replacement' in request('/containers/' + candidate + '/recreate')[0]
        request('/containers/' + candidate, method='DELETE')
        assert docker('volume', 'inspect', volume)
        # A fresh application container can still read data after both predecessors are deleted.
        reader = action('/containers/create', dict(values, name=PREFIX + '-reader', env='REVISION=reader'))
        ids.add(reader)
        assert 'second' in serving(reader) and 'third' in serving(reader)
        request('/containers/' + reader, method='DELETE')
        request('/volumes/' + volume, method='DELETE')
        assert volume not in docker('volume', 'ls', '-q').splitlines()
        print('PASS: rollback, confirmation and retained data after both predecessor deletions', flush=True)
        success, _ = request('/images/pull', {'image-pull-name': 'alpine:latest'}, 'POST')
        records = [record for line in success.splitlines() if (record := json.loads(line))['state'] != 'heartbeat']
        assert any(r['state'] == 'progress' for r in records) and records[-1]['state'] == 'success'
        failure, _ = request('/images/pull', {'image-pull-name': '127.0.0.1:1/no-such-image:latest'}, 'POST')
        assert [json.loads(line) for line in failure.splitlines() if json.loads(line)['state'] != 'heartbeat'][-1]['state'] == 'error'
        assert 'Error:' in request('/containers/' + PREFIX + '-missing')[0]
        print('PASS: dashboard create/configuration/reachability, exec+attach UTF-8/prompt/resize, logs/stats/events, prepare/start failures, rollback, confirm, retained data, pull progress/final failure and missing resource')
    finally:
        try:
            existing = set(docker('ps', '-aq', '--no-trunc').splitlines())
            for identifier in ids & existing:
                docker('rm', '-f', identifier)
            if volume in docker('volume', 'ls', '-q').splitlines():
                docker('volume', 'rm', volume)
            if network in docker('network', 'ls', '--format', '{{.Name}}').splitlines():
                docker('network', 'rm', network)
        except subprocess.CalledProcessError:
            # The enclosing harness still removes the entire disposable daemon and data volume.
            pass


if __name__ == '__main__':
    main()
