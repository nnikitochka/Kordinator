const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/webapp');
const source = fs.readFileSync(path.join(root, 'script/subsonic-now-playing.js'), 'utf8');
function setup() {
    const requests = [], callbacks = [], unsubscribed = [];
    let connected, pagehide;
    const stomp = {onConnect: [], subscribe(owner, topics, ready) { this.owner = owner; this.topics = topics; connected = ready; },
        unsubscribe(topic, owner) { unsubscribed.push([topic, owner]); }};
    const context = {URLSearchParams, console, window: {addEventListener(name, fn) { assert.equal(name, 'pagehide'); pagehide = fn; }},
        fetch(url, init) { return new Promise((resolve, reject) => requests.push({url, init, resolve, reject})); }};
    vm.runInNewContext(source, context);
    const refresh = context.watchSubsonicNowPlaying({baseUrl: '/app/rest/', csrfHeader: 'X-CSRF', csrfToken: 'token', stomp, owner: 'frame'},
        (current, recent) => callbacks.push({current, recent}));
    return {requests, callbacks, stomp, unsubscribed, refresh, connect: () => connected(), hide: () => pagehide()};
}
function respond(req, entries) {
    req.resolve({ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok', nowPlaying: {entry: entries}}})});
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('session REST response separates active and recent and adapts display fields', async () => {
    const s = setup(); s.connect();
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

test('notifications and reconnect reload; stale results cannot overwrite newer data', async () => {
    const s = setup(); s.connect();
    Object.values(s.stomp.topics)[0]({body: 'ignored'});
    assert.equal(s.requests.length, 2);
    respond(s.requests[1], [{id: 'new', active: true}]); await flush();
    respond(s.requests[0], [{id: 'old', active: true}]); await flush();
    assert.equal(s.callbacks.length, 1); assert.equal(s.callbacks[0].current[0].id, 'new');
    s.stomp.onConnect[0](); assert.equal(s.requests.length, 3);
    respond(s.requests[2], []); await flush(); assert.equal(s.callbacks[1].current.length, 0);
});

test('page disposal removes subscriptions and ignores pending results', async () => {
    const s = setup(); s.connect(); s.hide();
    assert.equal(s.stomp.onConnect.length, 0); assert.equal(s.unsubscribed.length, 4);
    respond(s.requests[0], [{id: 'late', active: true}]); await flush();
    assert.equal(s.callbacks.length, 0);
    Object.values(s.stomp.topics)[0](); assert.equal(s.requests.length, 1);
});

test('late connection after disposal does not request or register callbacks', () => {
    const s = setup(); s.hide(); s.connect();
    assert.equal(s.requests.length, 0); assert.equal(s.stomp.onConnect.length, 0);
});

test('both pages use REST watcher instead of internal subscriptions', () => {
    for (const name of ['right.jsp', 'playQueue.jsp']) {
        const jsp = fs.readFileSync(path.join(root, 'WEB-INF/jsp', name), 'utf8');
        assert.match(jsp, /subsonicNowPlaying\.jsp/); assert.match(jsp, /watchNowPlaying\(/);
        assert.doesNotMatch(jsp, /\/app\/nowPlaying\//);
    }
});

test('queue can refresh after the selected player and its songs load', async () => {
    const s = setup(); s.connect();
    s.refresh(); assert.equal(s.requests.length, 2);
    respond(s.requests[1], [{id: 'song', active: true, playerId: 4}]); await flush();
    assert.equal(s.callbacks[0].current[0].playerId, 4);
    const queue = fs.readFileSync(path.join(root, 'WEB-INF/jsp/playQueue.jsp'), 'utf8');
    assert.match(queue, /if \(initial && this\.refreshNowPlaying\) this\.refreshNowPlaying\(\)/);
});
