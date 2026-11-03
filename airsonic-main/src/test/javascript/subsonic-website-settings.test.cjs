const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup(fetch) {
    const context = {fetch, URLSearchParams, console: {error() {}}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/subsonic-website-settings.js'), 'utf8'), context);
    return context.createWebsiteSettings({url: '/rest/updateWebsiteSettings', csrfHeader: 'X-CSRF', csrfToken: 'token'});
}
const ok = () => ({ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok'}})});

test('sends authenticated CSRF-protected form and serializes rapid changes', async () => {
    const calls = [];
    let release;
    const api = setup(async (url, options) => {
        calls.push({url, options});
        if (calls.length === 1) await new Promise(resolve => { release = resolve; });
        return ok();
    });
    const first = api.set('viewAsList', true);
    const second = api.set('viewAsList', false);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, '/rest/updateWebsiteSettings');
    assert.equal(calls[0].options.credentials, 'same-origin');
    assert.equal(calls[0].options.headers['X-CSRF'], 'token');
    assert.equal(calls[0].options.body.get('viewAsList'), 'true');
    release();
    await Promise.all([first, second]);
    assert.equal(calls[1].options.body.get('viewAsList'), 'false');
});

test('reports failed responses and permits later updates', async () => {
    let count = 0;
    const api = setup(async () => ++count === 1 ? {ok: true, redirected: true} : ok());
    await assert.rejects(api.set('showSideBar', false));
    await api.set('showSideBar', true);
    assert.equal(count, 2);
});

test('rejects Subsonic error responses', async () => {
    const api = setup(async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {message: 'denied'}}})}));
    await assert.rejects(api.set('showSideBar', true), /denied/);
});
