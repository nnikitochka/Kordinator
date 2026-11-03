const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/webapp');
const source = fs.readFileSync(path.join(root, 'script/subsonic-now-playing.js'), 'utf8');
function setup() {
    const requests = [], callbacks = [];
    let pagehide, tick, cleared = false;
    const context = {URLSearchParams, console, window: {addEventListener(name, fn) { assert.equal(name, 'pagehide'); pagehide = fn; },
        setInterval(fn, delay) { assert.equal(delay, 5000); tick = fn; return 42; },
        clearInterval(id) { assert.equal(id, 42); cleared = true; }},
        fetch(url, init) { return new Promise((resolve, reject) => requests.push({url, init, resolve, reject})); }};
    vm.runInNewContext(source, context);
    const refresh = context.watchSubsonicNowPlaying({baseUrl: '/app/rest/', csrfHeader: 'X-CSRF', csrfToken: 'token'},
        (current, recent) => callbacks.push({current, recent}));
    return {requests, callbacks, refresh, tick: () => tick(), hide: () => pagehide(), cleared: () => cleared};
}
function respond(req, entries) {
    req.resolve({ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok', nowPlaying: {entry: entries}}})});
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('session REST response separates active and recent and adapts display fields', async () => {
    const s = setup();
    assert.equal(s.requests.length, 1);
    const req = s.requests[0];
    assert.equal(req.url, '/app/rest/getNowPlaying');
    assert.equal(req.init.method, 'POST'); assert.equal(req.init.credentials, 'same-origin');
    assert.equal(req.init.headers['X-CSRF'], 'token');
    assert.equal(req.init.body.get('includeWebsiteDetails'), 'true');
    respond(req, [{id: '12', active: true, displayUsername: 'user@player', displayArtist: 'artist', displayTitle: 'title', streamUrl: 'stream'},
        {id: '13', active: false, displayUsername: 'other'}]);
    await flush();
    assert.equal(s.callbacks.length, 1);
    assert.equal(s.callbacks[0].current[0].mediaFileId, '12');
    assert.equal(s.callbacks[0].current[0].username, 'user@player');
    assert.equal(s.callbacks[0].current[0].artist, 'artist');
    assert.equal(s.callbacks[0].recent[0].mediaFileId, '13');
});

test('periodic polling reloads and stale results cannot overwrite newer data', async () => {
    const s = setup(); s.tick();
    assert.equal(s.requests.length, 2);
    respond(s.requests[1], [{id: 'new', active: true}]); await flush();
    respond(s.requests[0], [{id: 'old', active: true}]); await flush();
    assert.equal(s.callbacks.length, 1); assert.equal(s.callbacks[0].current[0].id, 'new');
    s.tick(); assert.equal(s.requests.length, 3);
    respond(s.requests[2], []); await flush(); assert.equal(s.callbacks[1].current.length, 0);
});

test('page disposal stops polling and ignores pending results', async () => {
    const s = setup(); s.hide();
    assert.equal(s.cleared(), true);
    respond(s.requests[0], [{id: 'late', active: true}]); await flush();
    assert.equal(s.callbacks.length, 0);
    s.tick(); s.refresh(); assert.equal(s.requests.length, 1);
});

test('both pages use REST watcher instead of internal subscriptions', () => {
    for (const name of ['right.jsp', 'playQueue.jsp']) {
        const jsp = fs.readFileSync(path.join(root, 'WEB-INF/jsp', name), 'utf8');
        assert.match(jsp, /subsonicNowPlaying\.jsp/); assert.match(jsp, /watchNowPlaying\(/);
        assert.doesNotMatch(jsp, /\/app\/nowPlaying\//);
    }
});

test('queue can refresh after the selected player and its songs load', async () => {
    const s = setup();
    s.refresh(); assert.equal(s.requests.length, 2);
    respond(s.requests[1], [{id: 'song', active: true, playerId: 4}]); await flush();
    assert.equal(s.callbacks[0].current[0].playerId, 4);
    const queue = fs.readFileSync(path.join(root, 'WEB-INF/jsp/playQueue.jsp'), 'utf8');
    assert.match(queue, /if \(initial && this\.refreshNowPlaying\) this\.refreshNowPlaying\(\)/);
});
