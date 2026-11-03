const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../main/webapp');
const source = fs.readFileSync(path.join(root, 'script/subsonic-scan-status.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
function setup() {
    const requests = [], statuses = [], timers = new Map(), errors = [];
    let nextTimer = 0, pagehide;
    const context = {URLSearchParams, console: {error: error => errors.push(error)},
        window: {addEventListener(name, fn) { assert.equal(name, 'pagehide'); pagehide = fn; }},
        fetch(url, init) { return new Promise((resolve, reject) => requests.push({url, init, resolve, reject})); },
        setTimeout(fn, delay) { assert.equal(delay, 5000); const id = ++nextTimer; timers.set(id, fn); return id; },
        clearTimeout(id) { timers.delete(id); }};
    vm.runInNewContext(source, context);
    context.watchSubsonicScanStatus({url: '/airsonic/rest/getScanStatus', csrfHeader: 'X-CSRF', csrfToken: 'token'}, status => statuses.push(status));
    return {requests, statuses, timers, errors, hide: () => pagehide(), tick() { const [id, fn] = timers.entries().next().value; timers.delete(id); fn(); }};
}
function reply(request, scanning, count) {
    request.resolve({ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok', scanStatus: {scanning, count}}})});
}

test('polls immediately and detects scan start, progress and completion without overlapping requests', async () => {
    const s = setup();
    assert.equal(s.requests.length, 1);
    assert.equal(s.requests[0].url, '/airsonic/rest/getScanStatus');
    assert.equal(s.requests[0].init.method, 'POST');
    assert.equal(s.requests[0].init.credentials, 'same-origin');
    assert.equal(s.requests[0].init.headers['X-CSRF'], 'token');
    assert.equal(s.requests[0].init.body.get('f'), 'json');
    assert.equal(s.timers.size, 0);
    reply(s.requests[0], false, 0); await flush();
    s.tick(); reply(s.requests[1], true, 7); await flush();
    s.tick(); reply(s.requests[2], true, 42); await flush();
    s.tick(); reply(s.requests[3], false, 42); await flush();
    assert.deepEqual(s.statuses.map(x => [x.scanning, x.count]), [[false, 0], [true, 7], [true, 42], [false, 42]]);
    assert.equal(s.timers.size, 1);
    s.hide(); assert.equal(s.timers.size, 0);
});

test('retries failures and ignores pending responses after pagehide', async () => {
    const s = setup();
    s.requests[0].reject(new Error('offline')); await flush();
    assert.equal(s.errors.length, 1); assert.equal(s.timers.size, 1);
    s.tick(); s.hide(); reply(s.requests[1], true, 42); await flush();
    assert.equal(s.statuses.length, 0); assert.equal(s.timers.size, 0);
});

test('right panel uses REST polling and no scan WebSocket subscription', () => {
    const jsp = fs.readFileSync(path.join(root, 'WEB-INF/jsp/right.jsp'), 'utf8');
    assert.match(jsp, /subsonic-scan-status\.js/);
    assert.match(jsp, /watchSubsonicScanStatus\(/);
    assert.doesNotMatch(jsp, /\/topic\/scanStatus/);
});
