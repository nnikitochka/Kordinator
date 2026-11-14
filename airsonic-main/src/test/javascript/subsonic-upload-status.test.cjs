const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.join(__dirname, '../../main/webapp/');
const flush = () => new Promise(resolve => setImmediate(resolve));
const id = '01234567-89ab-cdef-0123-456789abcdef';
const ok = uploads => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok', uploadStatuses: {upload: uploads}}})});
function setup(fetch) {
    let timer, leave;
    const window = {
        setTimeout: (fn, delay) => { assert.equal(delay, 2000); timer = fn; return 1; },
        clearTimeout: () => { timer = undefined; },
        addEventListener: (event, fn) => { assert.equal(event, 'pagehide'); leave = fn; }
    };
    const context = {fetch, window, URLSearchParams, console: {error() {}}};
    vm.runInNewContext(fs.readFileSync(path.join(root, 'script/subsonic-upload-status.js'), 'utf8'), context);
    return {
        watch: callback => context.watchUploadStatus({url: '/rest/getUploadStatus', csrfHeader: 'X-CSRF', csrfToken: 'token'}, callback),
        tick: async () => { assert.ok(timer); const fn = timer; timer = undefined; fn(); await flush(); },
        leave: () => leave(), hasTimer: () => !!timer
    };
}
test('polls authenticated snapshots, handles progress and removes completed uploads', async () => {
    let count = 0; const updates = [], calls = [];
    const api = setup(async (url, options) => {
        calls.push({url, options});
        return ok(count++ === 0 ? [{transferId: id, bytesUploaded: 25, bytesTotal: 100}] : []);
    });
    api.watch(value => updates.push(value));
    await flush();
    assert.equal(updates[0][0].bytesUploaded, 25);
    assert.equal(calls[0].url, '/rest/getUploadStatus');
    assert.equal(calls[0].options.credentials, 'same-origin');
    assert.equal(calls[0].options.headers['X-CSRF'], 'token');
    await api.tick();
    assert.equal(updates[1].length, 0);
    api.leave();
    assert.equal(api.hasTimer(), false);
});
test('failed and malformed responses preserve previous display and retry', async () => {
    let n = 0, updates = 0;
    const api = setup(async () => ++n === 1 ? {ok: false} : n === 2 ? ok([{transferId: 'invalid'}]) : ok([]));
    api.watch(() => updates++);
    await flush(); await api.tick();
    assert.equal(updates, 0);
    await api.tick();
    assert.equal(updates, 1);
    api.leave();
});
test('does not overlap requests or update the page after leaving', async () => {
    let release;
    const api = setup(() => new Promise(resolve => { release = resolve; }));
    api.watch(() => assert.fail('page was closed'));
    await flush();
    assert.equal(api.hasTimer(), false);
    api.leave(); release(ok([])); await flush();
    assert.equal(api.hasTimer(), false);
});
test('upload page reconciles snapshots without websocket subscription', () => {
    const source = fs.readFileSync(path.join(root, 'WEB-INF/jsp/more.jsp'), 'utf8');
    const pageScript = fs.readFileSync(path.join(root, 'script/more-page.js'), 'utf8');
    assert.match(pageScript, /watchUploads\(updateUploads\)/);
    assert.match(source, /if \(!active\[this\.id/);
    assert.doesNotMatch(source, /\/user\/queue\/uploads\/status/);
});
