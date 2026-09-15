"""Read-only history response regression check against a running dashboard.

Usage: python3 history_smoke.py http://127.0.0.1:18080
"""
import sys
from html.parser import HTMLParser
from urllib.request import Request, urlopen


class Page(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.navigation = 0
        self.history_regions = []
        self.feed(html)

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if tag == 'nav' and attributes.get('aria-label') == 'Main navigation':
            self.navigation += 1
        if 'hx-history-elt' in attributes:
            self.history_regions.append(attributes.get('id'))


base = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:18080'
checks = 0
for path in ['/host', '/containers', '/containers/create', '/images', '/networks']:
    for headers, full_page in [({}, True), ({'HX-Request': 'true'}, False),
                               ({'HX-Request': 'true', 'HX-History-Restore-Request': 'true'}, True),
                               ({'HX-History-Restore-Request': 'true'}, True)]:
        with urlopen(Request(base + path, headers=headers), timeout=25) as response:
            assert response.status == 200
            assert response.headers['Cache-Control'] == 'no-store'
            page = Page(response.read().decode())
            assert page.navigation == int(full_page), (path, headers, page.navigation)
            assert page.history_regions == (['main-content'] if full_page else []), (path, headers)
            checks += 1
print(f'{checks} read-only history response checks passed')
