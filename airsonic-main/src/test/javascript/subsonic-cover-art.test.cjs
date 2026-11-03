const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '../../main/webapp');
const source = fs.readFileSync(path.join(root, 'script/subsonic-cover-art.js'), 'utf8');
function setup(replies) {
    const calls = [];
    const context = {URLSearchParams, fetch: async (url, init) => { calls.push({url, init}); return replies.shift(); }};
    vm.runInNewContext(source, context);
    return {calls, api: context.createSubsonicCoverArt({baseUrl: '/rest/', csrfHeader: 'X-CSRF', csrfToken: 'secret'})};
}
function response(body, ok = true, redirected = false) { return {ok, redirected, json: async () => ({'subsonic-response': body})}; }
test('search and set send encoded session POST requests with CSRF', async () => {
    const s = setup([response({status: 'ok', coverArtSearchResults: {image: [{imageUrl: 'http://image'}]}}), response({status: 'ok'})]);
    assert.equal((await s.api.search('A & B', 'album'))[0].imageUrl, 'http://image');
    await s.api.set(4, 'https://example.test/a?x=1&y=2');
    assert.equal(s.calls[0].url, '/rest/searchCoverArt');
    assert.equal(s.calls[0].init.body.get('artist'), 'A & B');
    assert.equal(s.calls[0].init.headers['X-CSRF'], 'secret');
    assert.equal(s.calls[0].init.credentials, 'same-origin');
    assert.equal(s.calls[1].url, '/rest/setCoverArt');
    assert.equal(s.calls[1].init.body.get('url'), 'https://example.test/a?x=1&y=2');
});
test('HTTP, redirect, invalid response and REST errors reject', async () => {
    for (const reply of [response({}, false), response({}, true, true), response({status: 'failed', error: {message: 'denied'}}), response({status: 'ok'})]) {
        await assert.rejects(setup([reply]).api.search('a', 'b'));
    }
});
test('cover page uses REST and binds result click without URL code injection', () => {
    const jsp = fs.readFileSync(path.join(root, 'WEB-INF/jsp/changeCoverArt.jsp'), 'utf8');
    assert.doesNotMatch(jsp, /StompClient|\/app\/coverart\//);
    assert.match(jsp, /\.on\("click"/);
    assert.doesNotMatch(jsp, /javascript:setImage\('\s*\+/);
});
