"""Read-only Compose dashboard tests using scripts/with-compose-fixture.sh."""
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError

base = sys.argv[1].rstrip('/')
project = next(a.split('=', 1)[1] for a in sys.argv if a.startswith('-PcomposeFixtureProject='))

def get(path, **headers):
    with urlopen(Request(base + path, headers=headers), timeout=30) as response:
        return response.read().decode()

query = urlencode({'project': project})
listing = get('/compose')
assert project in listing and 'data-section="/compose"' in listing
for path in ['/compose', '/compose/project?' + query, '/compose/service?' + query + '&service=worker']:
    for headers, full in [({}, True), ({'HX-Request': 'true'}, False), ({'HX-Request': 'true', 'HX-History-Restore-Request': 'true'}, True)]:
        html = get(path, **headers)
        assert ('aria-label="Main navigation"' in html) == full
page = get('/compose/project?' + query + '&tab=services')
for label in ['worker', 'stopped', 'healthy', 'exited', 'One-off', 'Project sections', 'Search services']:
    assert label in page, label
assert 'name="services"' in get('/compose/project?' + query + '&tab=logs')
for section in ['containers', 'volumes', 'networks']:
    assert 'Project sections' in get('/compose/project?' + query + '&tab=' + section)
empty = get('/compose/project?' + query + '&selection=selected')
assert 'No services selected' in empty and 'data-stream-url' not in empty
for params, present, absent in [('', ['worker-out', 'stopped-out', 'oneoff-out'], []),
        ('&selection=selected&services=worker', ['worker-out', 'oneoff-out'], ['stopped-out']),
        ('&selection=selected&services=worker&services=stopped', ['worker-out', 'stopped-out'], []),
        ('&selection=selected', [], ['worker-out', 'stopped-out'])]:
    logs = get('/compose/logs?' + query + params)
    assert 'event: done' in logs and 'event: failure' not in logs
    for text in present: assert text in logs, text
    for text in absent: assert text not in logs, text
logs = get('/compose/logs?' + query + '&selection=selected&services=stopped')
assert "data-channel='stdout'" in logs and "data-channel='stderr'" in logs
# Read live data, then close the connection; server heartbeat owns upstream cleanup.
with urlopen(base + '/compose/logs?' + query + '&selection=selected&services=worker&mode=live', timeout=15) as stream:
    while True:
        line = stream.readline().decode()
        if line.startswith('data: '):
            assert 'worker' in line
            break
try:
    get('/compose/project?project=definitely-absent-compose-project')
    raise AssertionError('Expected 404')
except HTTPError as error:
    assert error.code == 404
print('Compose dashboard pages, history, service selection, finite/live SSE and 404 passed', flush=True)
