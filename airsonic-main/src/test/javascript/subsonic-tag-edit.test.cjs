const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function setup(fetch) {
    const context = {fetch, URLSearchParams};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/subsonic-tag-edit.js'), 'utf8'), context);
    return context.createTagEdit({url: '/rest/editTags', csrfHeader: 'X-CSRF', csrfToken: 'token'});
}
function response(result) { return {ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok', tagEdit: {result}}})}; }
test('sends all tag fields with session, CSRF and proper encoding', async () => {
    let call;
    const api = setup(async (url, options) => { call = {url, options}; return response('UPDATED'); });
    assert.equal(await api.edit({id: 8, artist: 'A & B', title: 'Жизнь', track: '', year: '2020'}), 'UPDATED');
    assert.equal(call.url, '/rest/editTags');
    assert.equal(call.options.credentials, 'same-origin');
    assert.equal(call.options.headers['X-CSRF'], 'token');
    assert.equal(call.options.body.get('artist'), 'A & B');
    assert.equal(call.options.body.get('title'), 'Жизнь');
    assert.equal(call.options.body.get('track'), '');
});
test('returns skipped or parser error results', async () => {
    assert.equal(await setup(async () => response('SKIPPED')).edit({id: 1}), 'SKIPPED');
    assert.equal(await setup(async () => response('unsupported')).edit({id: 1}), 'unsupported');
});
test('rejects transport, login redirect and Subsonic errors', async () => {
    await assert.rejects(setup(async () => ({ok: false})).edit({id: 1}));
    await assert.rejects(setup(async () => ({ok: true, redirected: true})).edit({id: 1}));
    await assert.rejects(setup(async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {message: 'denied'}}})})).edit({id: 1}), /denied/);
});
