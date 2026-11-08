const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function setup(fetch, window = {}) {
    const context = {fetch, window, URLSearchParams, console: {error() {}}};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/subsonic-website-settings.js'), 'utf8'), context);
    return context.createWebsiteSettings({url: '/rest/updateWebsiteSettings', readUrl: '/rest/getWebsiteSettings', csrfHeader: 'X-CSRF', csrfToken: 'token'});
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

function clock() {
    let timer, leave;
    return {
        window: {
            setTimeout: (fn, delay) => { assert.equal(delay, 20000); timer = fn; return 1; },
            clearTimeout: () => { timer = undefined; },
            addEventListener: (event, fn) => { assert.equal(event, 'pagehide'); leave = fn; }
        },
        tick: async () => { assert.ok(timer); const fn = timer; timer = undefined; fn(); await flush(); },
        leave: () => leave(), hasTimer: () => !!timer
    };
}
const flush = () => new Promise(resolve => setImmediate(resolve));
const settingsResponse = (sidebar, list) => ({ok: true, json: async () => ({'subsonic-response': {
    status: 'ok', websiteSettings: {showSideBar: sidebar, viewAsList: list}
}})});

test('reads immediately and every 20 seconds, recovers from failure and stops on exit', async () => {
    const c = clock(), calls = [], updates = [];
    let count = 0;
    const api = setup(async (url, options) => {
        calls.push({url, options});
        count++;
        if (count === 2) throw Error('offline');
        return settingsResponse(count === 1, false);
    }, c.window);
    api.watch('showSideBar', value => updates.push(value));
    await flush();
    assert.deepEqual(updates, [true]);
    assert.equal(calls[0].url, '/rest/getWebsiteSettings');
    assert.equal(calls[0].options.headers['X-CSRF'], 'token');
    await c.tick();
    assert.deepEqual(updates, [true]);
    await c.tick();
    assert.deepEqual(updates, [true, false]);
    c.leave();
    assert.equal(c.hasTimer(), false);
});

test('ignores a stale in-flight read when the user changes a setting', async () => {
    const c = clock(), updates = [];
    let release;
    const api = setup(url => url.includes('getWebsiteSettings') ?
        new Promise(resolve => { release = resolve; }) : Promise.resolve(ok()), c.window);
    api.watch('viewAsList', value => updates.push(value));
    await flush();
    const write = api.set('viewAsList', true);
    release(settingsResponse(false, false));
    await write;
    await flush();
    assert.deepEqual(updates, []);
    c.leave();
});

test('does not update or reschedule after the page is closed during a read', async () => {
    const c = clock();
    let release;
    const api = setup(() => new Promise(resolve => { release = resolve; }), c.window);
    api.watch('viewAsList', () => assert.fail('page has closed'));
    await flush();
    assert.equal(c.hasTimer(), false);
    c.leave();
    release(settingsResponse(false, true));
    await flush();
    assert.equal(c.hasTimer(), false);
});

test('website settings pages use polling without settings subscriptions', () => {
    for (const page of ['top', 'mediaMain', 'playlists']) {
        const source = fs.readFileSync(path.join(__dirname, '../../main/webapp/WEB-INF/jsp', page + '.jsp'), 'utf8');
        assert.match(source, /websiteSettingsApi\.watch\(/);
        assert.doesNotMatch(source, /\/user\/queue\/settings\//);
    }
});
