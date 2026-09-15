"""Read-only host overview checks against a running dashboard and its local daemon."""
import json
import subprocess
import sys
from html.parser import HTMLParser
from urllib.request import urlopen

class Metrics(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.values = []
        self.strong = False
        self.feed(html)
    def handle_starttag(self, tag, attrs):
        self.strong = tag == 'strong'
    def handle_endtag(self, tag):
        if tag == 'strong':
            self.strong = False
    def handle_data(self, data):
        if self.strong and data.strip().isdigit():
            self.values.append(int(data))

base = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:18081'
ids = subprocess.check_output(['docker', 'ps', '-aq'], text=True).split()
containers = json.loads(subprocess.check_output(['docker', 'inspect', *ids])) if ids else []
states = [c['State'] for c in containers]
expected = [sum(s['Status'] == 'running' for s in states),
            sum(s.get('Health', {}).get('Status') == 'unhealthy' for s in states),
            sum(s['Status'] == 'exited' and s['ExitCode'] != 0 for s in states),
            sum(s['Status'] in ('created', 'exited', 'dead') for s in states)]
with urlopen(base + '/host') as response:
    html = response.read().decode()
assert Metrics(html).values == expected, (Metrics(html).values, expected)
assert 'Needs attention' in html and 'Compose projects' in html and 'Live events' in html
with urlopen(base + '/host/summary') as response:
    summary = response.read().decode()
info = json.loads(subprocess.check_output(['docker', 'info', '--format', '{{json .}}']))
assert info['ServerVersion'] in summary and str(info['NCPU']) + ' CPU' in summary
with urlopen(base + '/') as response:
    assert response.url.endswith('/host')
print('Host metrics match Docker; sidebar metadata and default route passed')
