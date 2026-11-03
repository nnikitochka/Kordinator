const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function setup(fetch) {
    const context = {fetch, URLSearchParams, console: {error() {}}};
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
test('reports errors and permits subsequent requests', async () => {
    let count = 0;
    const api = setup(async () => ++count === 1 ? {ok: true, redirected: true} : ok());
    await assert.rejects(api.send(1, 'clear', {}));
    await api.send(1, 'get', {});
    assert.equal(count, 2);
});
