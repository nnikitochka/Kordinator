const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('airsonic-main/src/main/webapp/script/playlist-import.js', 'utf8');

function setup(fetch) {
    const button = {disabled: false}, error = {hidden: true, textContent: ''};
    Object.defineProperty(error, 'innerHTML', {set() { assert.fail('errors must be rendered as text'); }});
    let submit;
    const form = {addEventListener: (event, listener) => { assert.equal(event, 'submit'); submit = listener; },
        querySelector: () => button};
    const data = {selectedFile: 'Music & tracks.m3u'};
    const imported = [];
    const context = {fetch, FormData: function (received) { assert.equal(received, form); return data; }};
    vm.runInNewContext(code, context);
    context.bindPlaylistImport(form, error, {url: '/airsonic/rest/importPlaylist', csrfHeader: 'X-CSRF-TOKEN',
        csrfToken: 'token', imported: value => imported.push(value)});
    return {button, error, imported, data, submit: () => submit({preventDefault() {}})};
}
const response = body => ({ok: true, json: async () => ({'subsonic-response': body})});

test('imports the selected file with CSRF and lets the browser encode multipart boundaries', async () => {
    let request;
    const page = setup(async (url, options) => { request = {url, options}; return response({status: 'ok', playlistImport: {id: '42', name: 'Музыка'}}); });
    await page.submit();
    assert.equal(request.url, '/airsonic/rest/importPlaylist?f=json&c=airsonic-web');
    assert.equal(request.options.method, 'POST');
    assert.equal(request.options.headers['X-CSRF-TOKEN'], 'token');
    assert.equal(request.options.headers['Content-Type'], undefined);
    assert.equal(request.options.credentials, 'same-origin');
    assert.equal(request.options.cache, 'no-store');
    assert.equal(request.options.body, page.data);
    assert.deepEqual(JSON.parse(JSON.stringify(page.imported)), [{id: '42', name: 'Музыка'}]);
    assert.equal(page.error.hidden, true);
    assert.equal(page.button.disabled, false);
});

test('blocks repeated submissions while pending and permits retry after an error', async () => {
    let finish, requests = 0;
    const page = setup(() => { requests++; return new Promise(resolve => { finish = resolve; }); });
    const first = page.submit();
    assert.equal(page.button.disabled, true);
    await page.submit();
    assert.equal(requests, 1);
    finish(response({status: 'failed', error: {message: '<img src=x onerror=alert(1)>'}}));
    await first;
    assert.equal(page.error.hidden, false);
    assert.equal(page.error.textContent, '<img src=x onerror=alert(1)>');
    assert.equal(page.button.disabled, false);
    assert.equal(page.imported.length, 0);
    const retry = page.submit();
    assert.equal(requests, 2);
    assert.equal(page.error.hidden, true);
    assert.equal(page.error.textContent, '');
    finish(response({status: 'ok', playlistImport: {id: '1'}}));
    await retry;
    assert.equal(page.imported.length, 1);
});

test('rejects HTTP errors, login redirects, malformed envelopes, missing IDs and network failures', async () => {
    for (const next of [{ok: false}, {...response({status: 'ok'}), redirected: true},
        {ok: true, json: async () => ({})}, response({status: 'ok'}), response({status: 'ok', playlistImport: {}}),
        {ok: true, json: async () => { throw new Error('Invalid JSON'); }}, new Error('Offline')]) {
        const page = setup(async () => { if (next instanceof Error) throw next; return next; });
        await page.submit();
        assert.equal(page.imported.length, 0);
        assert.equal(page.error.hidden, false);
        assert.notEqual(page.error.textContent, '');
        assert.equal(page.button.disabled, false);
    }
});
