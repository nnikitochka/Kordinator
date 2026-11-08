const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function setup(fetch, window = {}) {
    const context = {fetch, window, URLSearchParams, console: {error() {}}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/subsonic-live-queue.js'), 'utf8'), context);
    return context.createLiveQueue({url: '/rest/livePlayQueue', csrfHeader: 'X-CSRF', csrfToken: 'secret'});
}
const ok = () => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok', livePlayQueue: {revision: 12}}})});
test('sends ordered commands with repeated IDs and CSRF', async () => {
    const calls = [];
    let release;
    const api = setup(async (url, options) => {
        calls.push({url, options});
        if (calls.length === 1) await new Promise(resolve => { release = resolve; });
        return ok();
    });
    const first = api.send(42, 'add', {id: [3, 4]});
    const second = api.send(42, 'clear', {});
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/rest/livePlayQueue');
    assert.equal(calls[0].options.credentials, 'same-origin');
    assert.equal(calls[0].options.headers['X-CSRF'], 'secret');
    assert.deepEqual(calls[0].options.body.getAll('id'), ['3', '4']);
    assert.equal(calls[0].options.body.get('playerId'), '42');
    release();
    assert.equal((await first).revision, 12);
    await second;
    assert.equal(calls[1].options.body.get('action'), 'clear');
});
test('polls without overlap and stops after disposal', async () => {
    let timer, delay, release, requests = 0, received = 0, cleared = false;
    const window = {setTimeout(fn, ms) { timer = fn; delay = ms; return 17; },
        clearTimeout(id) { assert.equal(id, 17); cleared = true; }};
    const api = setup(async () => { requests++; await new Promise(resolve => { release = resolve; }); return ok(); }, window);
    const stop = api.watch(7, () => received++);
    assert.equal(delay, 5000);
    timer();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(requests, 1);
    stop();
    release();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(received, 0);
    assert.equal(cleared, true);
});

test('polling applies changed snapshots only and never repeats playback instructions', () => {
    const jsp = fs.readFileSync(path.join(__dirname, '../../main/webapp/WEB-INF/jsp/playQueue.jsp'), 'utf8');
    const method = jsp.match(/        acceptQueue\(queue, initial, suppressPlaylistDownload, polling\) \{[\s\S]*?\n        \},/)[0];
    const context = {};
    vm.runInNewContext('var queue = {' + method + '};', context);
    let updates = 0, skips = 0;
    Object.assign(context.queue, {player: {tech: 'WEB'}, playQueueCallback() { updates++; return true; },
        playQueueSkipCallback() { skips++; }});
    const state = {entry: [{id: 1}], playStatus: 'PLAYING', repeatStatus: 'OFF', gain: 0.5, startPlayerAt: 0};
    context.queue.acceptQueue(state, false, true, false);
    context.queue.acceptQueue({...state, revision: 2}, false, true, true);
    assert.equal(updates, 1); assert.equal(skips, 1);
    context.queue.acceptQueue({...state, entry: [{id: 2}]}, false, true, true);
    assert.equal(updates, 2); assert.equal(skips, 1);
    assert.doesNotMatch(jsp, /\/user\/queue\/playqueues\//);
});
test('reports errors and permits subsequent requests', async () => {
    let count = 0;
    const api = setup(async () => ++count === 1 ? {ok: true, redirected: true} : ok());
    await assert.rejects(api.send(1, 'clear', {}));
    await api.send(1, 'get', {});
    assert.equal(count, 2);
});

test('player polling updates options and handles a deleted selected player', () => {
    const jsp = fs.readFileSync(path.join(__dirname, '../../main/webapp/WEB-INF/jsp/playQueue.jsp'), 'utf8');
    const method = jsp.match(/        updatePlayers\(players\) \{[\s\S]*?\n        \},/)[0];
    const removed = [], updated = [], changes = [];
    const options = [{value: '1'}, {value: '2'}];
    const context = {$: value => value === '#playerSelector option'
        ? {each(fn) { options.forEach(option => fn.call(option)); }}
        : {remove() { removed.push(value.value); }}};
    vm.runInNewContext('var queue = {' + method + '};', context);
    Object.assign(context.queue, {player: {id: 2}, onPlayerUpdated(player) { updated.push(player.id); },
        onPlayerChanged(playerId) { changes.push(playerId); }});
    context.queue.updatePlayers([{id: 1}, {id: 3}]);
    assert.deepEqual(removed, ['2']);
    assert.deepEqual(updated, [1, 3]);
    assert.deepEqual(changes, [null]);
    assert.doesNotMatch(jsp, /\/user\/queue\/players\//);
});
