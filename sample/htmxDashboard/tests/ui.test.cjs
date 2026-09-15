const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { JSDOM } = require('jsdom');
const source = (file) => readFileSync(join(__dirname, '../src/linuxX64Main/kotlin', file), 'utf8');
const script = (file) => source(file).split('"""')[1].replaceAll('$elementId', 'stream-test');
function shell(content, url = 'http://localhost/containers') {
  const dom = new JSDOM(`<div id="alerts"></div><div id="request-status"></div><button id="theme-toggle"><span data-theme-sun>Sun</span><span data-theme-moon hidden>Moon</span></button><nav><a href="/containers" data-section="/containers">Containers</a><a href="/images" data-section="/images">Images</a></nav><main id="main-content">${content}</main><dialog id="confirm-dialog"><h2 id="confirm-title"></h2><p id="confirm-message"></p><label id="confirm-label"><input id="confirm-input"></label><button id="confirm-submit">Confirm</button></dialog>`, { url, runScripts: 'outside-only' });
  dom.window.htmx = { config: {}, process: () => {} };
  dom.window.scrollTo = () => {};
  const modal = dom.window.document.querySelector('dialog');
  modal.showModal = () => { modal.open = true; };
  modal.close = (value) => { modal.open = false; modal.returnValue = value; modal.dispatchEvent(new dom.window.Event('close')); };
  dom.window.eval(script('ui/script.kt'));
  return dom;
}
function emit(dom, name, detail, target = dom.window.document) {
  const event = new dom.window.CustomEvent(name, { bubbles: true, cancelable: true, detail });
  target.dispatchEvent(event); return event;
}
function listing() {
  return `<h1 tabindex="-1">Containers</h1><input data-search><select data-filter><option value="">All</option><option>running</option><option>exited</option></select><select data-sort><option>name</option><option>state</option></select><table><tbody><tr data-name="zeta" data-state="running" data-search-text="zeta alpine id-1"><td>Zeta</td></tr><tr data-name="alpha" data-state="exited" data-search-text="alpha redis id-2"><td>Alpha</td></tr></tbody></table><div data-no-results hidden></div><span data-result-count></span><button data-reset-filters>Reset</button>`;
}
test('search, state and sorting round-trip through the URL; empty results can be reset', () => {
  const dom = shell(listing(), 'http://localhost/containers?q=redis&state=exited'); const doc = dom.window.document;
  assert.equal(doc.querySelector('[data-result-count]').textContent, '1 of 2 resources');
  assert.equal(doc.querySelector('tbody tr').dataset.name, 'alpha');
  assert.equal(doc.querySelector('[data-section="/containers"]').getAttribute('aria-current'), 'page');
  assert.equal(dom.window.htmx.config.historyCacheSize, 0);
  doc.querySelector('[data-search]').value = 'missing'; emit(dom, 'input', null, doc.querySelector('[data-search]'));
  assert.equal(doc.querySelector('[data-no-results]').hidden, false);
  assert.equal(new URL(dom.window.location.href).searchParams.get('q'), 'missing');
  doc.querySelector('[data-reset-filters]').click();
  assert.equal(doc.querySelector('[data-result-count]').textContent, '2 of 2 resources');
  assert.equal(new URL(dom.window.location.href).searchParams.has('state'), false);
  dom.window.close();
});
test('destructive confirmation does not send on cancel and requires the exact volume name', () => {
  const dom = shell('<h1>Volumes</h1><button id="remove" data-confirm-name="db-data">Delete volume</button>');
  const doc = dom.window.document; let calls = 0;
  const elt = doc.querySelector('#remove');
  const request = () => emit(dom, 'htmx:confirm', { question: 'Delete db-data and its data?', elt, issueRequest: () => calls++ });
  assert.equal(request().defaultPrevented, true);
  assert.equal(doc.querySelector('#confirm-submit').disabled, true);
  doc.querySelector('#confirm-dialog').close('cancel'); assert.equal(calls, 0);
  request(); const input = doc.querySelector('#confirm-input'); input.value = 'db'; emit(dom, 'input', null, input);
  assert.equal(doc.querySelector('#confirm-submit').disabled, true);
  input.value = 'db-data'; emit(dom, 'input', null, input);
  assert.equal(doc.querySelector('#confirm-submit').disabled, false);
  doc.querySelector('#confirm-dialog').close('confirm'); assert.equal(calls, 1);
  dom.window.close();
});
test('request feedback keeps other in-flight actions disabled and errors retain the page', () => {
  const dom = shell('<h1>Original page</h1><button id="one">Stop</button><button id="two">Start</button>'); const doc = dom.window.document;
  const one = doc.querySelector('#one'), two = doc.querySelector('#two');
  emit(dom,'htmx:beforeSend',{elt:one}); emit(dom,'htmx:beforeSend',{elt:two});
  emit(dom,'htmx:afterRequest',{elt:one});
  assert.equal(one.disabled,false); assert.equal(two.disabled,true);
  emit(dom,'htmx:sendError',{}); assert.equal(doc.querySelector('h1').textContent,'Original page');
  assert.equal(doc.querySelectorAll('[role=alert]').length,1);
  emit(dom,'htmx:afterRequest',{elt:two}); assert.equal(doc.querySelector('#request-status').textContent,'');
  dom.window.close();
});
test('repeat fields serialize values without browser storage and remain editable after errors', () => {
  const dom = shell(`<h1>Create</h1><form data-config-form><div data-repeat="env"><div class="repeat-fields"><div class="repeat-row"><input value="TOKEN=secret-test"><button type="button" data-remove-row>Remove</button></div></div><button type="button" data-add-row>Add</button></div></form>`);
  const doc = dom.window.document; doc.querySelector('[data-add-row]').click();
  doc.querySelectorAll('.repeat-row input')[1].value = 'GREETING=hello world';
  const detail={elt:doc.querySelector('form'),parameters:{}}; emit(dom,'htmx:configRequest',detail);
  assert.equal(detail.parameters.env,'TOKEN=secret-test\nGREETING=hello world');
  assert.equal(dom.window.localStorage.length,0);
  const swap={xhr:{status:422},shouldSwap:false,isError:true}; emit(dom,'htmx:beforeSwap',swap);
  assert.equal(swap.shouldSwap,true); assert.equal(swap.isError,false);
  assert.equal(doc.querySelector('input').value,'TOKEN=secret-test');
  dom.window.close();
});
test('stream keeps 200 records, respects manual scrolling, reconnects once and disposes', () => {
  const dom = new JSDOM(`<section id="stream-test" data-stream-url="/logs" data-append="true"><span data-stream-status></span><div data-stream-output></div><button data-follow></button><button data-reconnect></button></section>`,{runScripts:'outside-only'});
  const sockets=[];
  class Stream { constructor(){this.handlers={};this.closed=false;sockets.push(this);} close(){this.closed=true;} addEventListener(type,fn){this.handlers[type]=fn;} }
  dom.window.EventSource=Stream; dom.window.eval(script('ui/liveStream.kt'));
  const doc=dom.window.document; const output=doc.querySelector('[data-stream-output]');
  Object.defineProperty(output,'clientHeight',{value:100});
  Object.defineProperty(output,'scrollHeight',{get:()=>Math.max(100,output.children.length*10)});
  for(let i=0;i<205;i++) sockets[0].onmessage({data:'<span>'+i+'</span>'});
  assert.equal(output.children.length,200); assert.equal(output.firstElementChild.textContent,'5');
  output.scrollTop=20; output.dispatchEvent(new dom.window.Event('scroll'));
  assert.equal(doc.querySelector('[data-follow]').getAttribute('aria-pressed'),'false');
  sockets[0].onmessage({data:'latest'}); assert.equal(output.scrollTop,20);
  doc.querySelector('[data-reconnect]').click(); assert.equal(sockets.length,2); assert.equal(sockets[0].closed,true);
  sockets[0].onmessage({data:'stale'}); assert.equal(output.children.length,0);
  sockets[1].handlers.done(); assert.equal(doc.querySelector('[data-stream-status]').textContent,'Stream ended');
  doc.querySelector('[data-reconnect]').click();
  doc.querySelector('section').dispatchEvent(new dom.window.Event('dashboard:dispose'));
  assert.equal(sockets[2].closed,true); doc.querySelector('[data-reconnect]').click(); assert.equal(sockets.length,3);
  dom.window.close();
});

test('theme icon toggles palette and accessible action while persisting only the preference', () => {
  const dom=shell('<h1>Containers</h1>');const doc=dom.window.document;const button=doc.querySelector('#theme-toggle');
  assert.equal(button.getAttribute('aria-label'),'Switch to light theme');
  button.click();assert.equal(doc.documentElement.dataset.theme,'light');
  assert.equal(button.getAttribute('aria-label'),'Switch to dark theme');
  assert.equal(button.querySelector('[data-theme-moon]').hidden,false);
  assert.equal(dom.window.localStorage.getItem('dashboard-theme'),'light');
  button.click();assert.equal(doc.documentElement.dataset.theme,'dark');
  dom.window.close();
});

test('Cancel does not put form values in a GET and breadcrumb navigation refreshes the HTMX path', () => {
  const dom=shell(listing(),'http://localhost/containers?q=redis');const doc=dom.window.document;
  const main=doc.querySelector('main');let refreshed=null;dom.window.htmx.process=el=>{refreshed=el.getAttribute('hx-get');};
  dom.window.history.pushState({},'', '/containers/id-2');
  main.innerHTML='<h1>Alpha</h1><a href="/containers" hx-get="/containers">Back</a><form data-config-form><a id="cancel" href="/containers">Cancel</a><div data-repeat="env"><input value="TOKEN=private"></div></form>';
  emit(dom,'htmx:afterSettle',{target:main});
  assert.equal(refreshed,'/containers?q=redis');
  const detail={elt:doc.querySelector('#cancel'),parameters:{}};emit(dom,'htmx:configRequest',detail);
  assert.deepEqual(detail.parameters,{});
  dom.window.close();
});
