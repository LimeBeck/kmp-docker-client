"""Opt-in HTTP smoke test against a running dashboard and real Docker.

Creates uniquely named disposable resources and removes only those resources.
Never calls prune. Example: python3 http_smoke.py http://127.0.0.1:18080
"""
import sys
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from urllib.parse import urlencode

base = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:18080'
prefix = 'dashboard-redesign-' + uuid.uuid4().hex[:10]
containers = set()
volume = prefix + '-data'
volume_created = False
checks = 0


def request(path, method='GET', fields=None, expected=200):
    global checks
    data = urlencode(fields).encode() if fields is not None else (b'' if method == 'POST' else None)
    req = Request(base + path, data=data, method=method, headers={'HX-Request': 'true', 'Content-Type': 'application/x-www-form-urlencoded'})
    try:
        response = urlopen(req, timeout=25)
    except HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        assert response.status == expected, (path, response.status, body[:300])
        checks += 1
        return response.headers, body


def configuration(name=prefix, image='alpine:latest'):
    return {'name': name, 'image': image, 'cmd': '/bin/sh\n-c\nprintf "dashboard-smoke-output\\n"; exec sleep 300', 'env': 'CHECK=retained', 'volumes': volume + ':/data'}


def replacement(previous):
    headers, _ = request(previous + '/recreate', 'POST', configuration())
    candidate = headers['HX-Redirect']
    assert candidate and candidate != previous
    containers.add(candidate)
    return candidate

try:
    for page in ['/containers', '/containers/create', '/images', '/images/pull', '/volumes', '/networks', '/system']:
        headers, body = request(page)
        assert '<h1' in body
        assert headers['Cache-Control'] == 'no-store'
    invalid = configuration()
    invalid['env'] = 'INVALID ENV'
    _, body = request('/containers/create', 'POST', invalid, 422)
    assert prefix in body and 'INVALID ENV' in body and 'Environment: enter KEY=value' in body
    request('/volumes', 'POST', {'name': volume})
    volume_created = True
    headers, _ = request('/containers/create', 'POST', configuration())
    original = headers['HX-Redirect']
    assert original.startswith('/containers/')
    containers.add(original)
    for tab, marker in [('overview', 'Published ports'), ('logs', 'Container logs'), ('terminal', 'Open a terminal'), ('configuration', 'retained')]:
        _, body = request(original + '?tab=' + tab)
        assert marker in body
    request(original + '/stop', 'POST')
    _, logs = request(original + '/logs')
    assert 'dashboard-smoke-output' in logs and 'event: done' in logs
    request(original + '/start', 'POST')
    _, body = request(original + '/recreate', 'POST', configuration(image='missing-dashboard-smoke:invalid'), 422)
    assert 'missing-dashboard-smoke:invalid' in body
    _, body = request(original + '?tab=terminal')
    assert 'Open a terminal' in body
    candidate = replacement(original)
    _, body = request(candidate)
    assert 'Replacement needs verification' in body
    request(candidate + '/rollback', 'POST')
    containers.discard(candidate)
    candidate = replacement(original)
    request(candidate + '/confirm', 'POST')
    containers.discard(original)
    request(candidate, 'DELETE')
    containers.discard(candidate)
    _, body = request('/volumes')
    assert volume in body  # Container deletion retained the named volume.
    request('/volumes/' + volume, 'DELETE')
    volume_created = False
    _, body = request('/volumes/' + prefix + '-missing', 'DELETE', expected=422)
    assert 'Something went wrong' in body
    print(f'PASS: {checks} HTTP checks; create, stopped logs, form recovery, replacement/rollback/confirm, retained volume, error feedback')
finally:
    for path in list(containers):
        try:
            request(path, 'DELETE')
        except Exception as error:
            print('Cleanup needs attention:', path, str(error)[:200], file=sys.stderr)
    if volume_created:
        try:
            request('/volumes/' + volume, 'DELETE')
        except Exception as error:
            print('Cleanup needs attention:', volume, str(error)[:200], file=sys.stderr)
