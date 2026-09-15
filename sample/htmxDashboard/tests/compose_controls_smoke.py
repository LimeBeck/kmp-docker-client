"""Mutating checks confined to scripts/with-compose-fixture.sh's disposable project."""
import json
import subprocess
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

base = sys.argv[1].rstrip('/')
project = next(a.split('=', 1)[1] for a in sys.argv if a.startswith('-PcomposeFixtureProject='))
assert project.startswith('kmp-compose-test-')

def docker(*args):
    return subprocess.check_output(['docker', '--host', 'unix:///var/run/docker.sock', *args], text=True).strip()

def request(path, method='GET'):
    with urlopen(Request(base + path, method=method, headers={'HX-Request': 'true'}), timeout=45) as response:
        return response.read().decode()

query = urlencode({'project': project, 'services': 'control'})
control = docker('ps', '-aq', '--filter', f'label=com.docker.compose.project={project}',
                 '--filter', 'label=com.docker.compose.service=control')
workers = docker('ps', '-q', '--filter', f'label=com.docker.compose.project={project}',
                 '--filter', 'label=com.docker.compose.service=worker').splitlines()
before = json.loads(docker('inspect', control))[0]
page = request('/compose/project?' + urlencode({'project': project}))
assert 'Start all' in page and 'Stop all' in page and 'Restart all' in page
assert 'hx-confirm=' in page
for action, state in [('stop', 'exited'), ('start', 'running'), ('restart', 'running')]:
    result = request(f'/compose/actions/{action}?' + query, 'POST')
    assert '1 of 1 container requests succeeded.' in result
    assert json.loads(docker('inspect', control))[0]['State']['Status'] == state
assert before['Mounts'] == json.loads(docker('inspect', control))[0]['Mounts']
assert all(json.loads(docker('inspect', worker))[0]['State']['Running'] for worker in workers)
assert 'No matching regular replicas' in request('/compose/actions/start?' + urlencode({'project': project, 'services': 'absent'}), 'POST')
for path in ['/compose/actions/remove?' + query, '/compose/actions/start?project=', '/compose/actions/start?' + urlencode({'project': project, 'services': ''})]:
    try:
        request(path, 'POST')
        raise AssertionError('Expected validation failure')
    except HTTPError as e:
        assert e.code == 400

# A real Engine error must remain visible alongside an already-running replica's success.
broken = docker('create', '--label', f'com.docker.compose.project={project}',
                '--label', 'com.docker.compose.service=control',
                '--label', 'com.docker.compose.oneoff=False',
                'alpine:3.21', '/does-not-exist')
try:
    result = request('/compose/actions/start?' + query, 'POST')
    assert '1 of 2 container requests succeeded.' in result, result
    assert 'Failed:' in result and 'Succeeded' in result
finally:
    docker('rm', '-f', broken)
print('Compose controls: actions, per-container errors, validation, isolation and volume retention passed', flush=True)
