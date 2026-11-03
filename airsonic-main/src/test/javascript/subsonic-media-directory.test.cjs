const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function setup(fetch) {
    const context = {fetch, URLSearchParams};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/subsonic-media-directory.js'), 'utf8'), context);
    return context.createMediaDirectory({url: '/rest/getMusicDirectory', csrfHeader: 'X-CSRF', csrfToken: 'token'});
}
function response(value) { return {ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok', websiteDirectory: value}})}; }
test('encodes multiple IDs and folder paths and maps directory fields', async () => {
    let call;
    const api = setup(async (url, options) => { call = {url, options}; return response({contentType: 'album', detail: {id: 2}, file: [{id: 9}]}); });
    const result = await api.get([2, 3], {'5': ['A&B: C', 'other']});
    assert.equal(call.url, '/rest/getMusicDirectory');
    assert.equal(call.options.headers['X-CSRF'], 'token');
    assert.equal(call.options.credentials, 'same-origin');
    assert.deepEqual(call.options.body.getAll('id'), ['2', '3']);
    assert.deepEqual(call.options.body.getAll('folderPath'), ['5:A&B: C', '5:other']);
    assert.equal(result.id, 2);
    assert.equal(result.contentType, 'album');
    assert.equal(result.files[0].id, 9);
    assert.equal(result.ancestors.length, 0);
});
test('ignores an older response arriving after a newer directory', async () => {
    const pending = [];
    const api = setup(() => new Promise(resolve => pending.push(resolve)));
    const first = api.get([1], {}), second = api.get([2], {});
    pending[1](response({contentType: 'artist', detail: {id: 2}}));
    assert.equal((await second).id, 2);
    pending[0](response({contentType: 'artist', detail: {id: 1}}));
    assert.equal(await first, null);
});
test('rejects failed responses and preserves redirect markers', async () => {
    const api = setup(async () => response({contentType: 'accessDenied'}));
    assert.equal((await api.get([3], {})).contentType, 'accessDenied');
    const failing = setup(async () => ({ok: true, redirected: true}));
    await assert.rejects(failing.get([3], {}));
});
