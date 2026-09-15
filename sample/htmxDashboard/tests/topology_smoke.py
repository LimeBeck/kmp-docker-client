"""Topology smoke with isolated containers/networks; uses the Compose fixture harness."""
import json
import subprocess
import sys
from urllib.parse import urlencode
from urllib.request import urlopen

base = sys.argv[1].rstrip('/')
project = next(a.split('=', 1)[1] for a in sys.argv if a.startswith('-PcomposeFixtureProject='))
def docker(*args):
    return subprocess.check_output(['docker', '--host', 'unix:///var/run/docker.sock', *args], text=True).strip()
def get(params):
    return urlopen(base + '/compose/project?' + urlencode({'project': project, **params}), timeout=30).read().decode()
network = docker('network', 'create', project + '-extra')
container = None
try:
    container = docker('create', '--name', project + '-topology', '--network', project + '_default',
        '--label', 'com.docker.compose.project=' + project, '--label', 'com.docker.compose.service=topology-test',
        '--label', 'com.docker.compose.oneoff=False', '-p', '127.0.0.1::8080', '--expose', '9000',
        'alpine:3.21', 'sleep', '120')
    docker('network', 'connect', '--alias', 'topology-alias', network, container)
    docker('start', container)
    info = json.loads(docker('inspect', container))[0]
    page = get({'container': container})
    assert page.count('data-node-container="' + container + '"') == 2
    assert project + '-extra' in page and 'topology-alias' in page
    assert 'binding-loopback' in page and 'data-binding-active="true"' in page
    assert info['NetworkSettings']['Ports']['8080/tcp'][0]['HostPort'] in page
    assert '9000/tcp' in page and 'Volumes and mounts' in page
    for detail in ['overview', 'inspect', 'config', 'stats', 'logs']:
        html = get({'container': container, 'detail': detail})
        assert 'Selected container sections' in html and 'Something went wrong' not in html
        if detail in ['stats', 'logs']:
            assert '/containers/' + container + '/' + detail in html
    docker('stop', '--time', '1', container)
    page = get({'container': container})
    assert 'Configured only' in page
    assert 'data-binding-active="false"' in page
    assert 'Statistics are available for running containers.' in get({'container': container, 'detail': 'stats'})
    assert 'Container is not part of this project/service snapshot.' in get({'container': 'outside-project'})
    print('Topology: multiple networks, aliases, binding scope, exposed ports, panels and stopped state passed')
finally:
    if container:
        docker('rm', '-f', container)
    docker('network', 'rm', network)
