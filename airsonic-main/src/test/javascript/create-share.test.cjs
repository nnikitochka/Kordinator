const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('airsonic-main/src/main/webapp/script/create-share.js', 'utf8');
const ok = {ok: true, json: async () => ({'subsonic-response': {status: 'ok', shares: {share: [{url: 'https://host/airsonic/ext/share/abc?jwt=a&b=c'}]}}})};
function setup(fetch, query = '?id=42&i=1&i=0&u=attacker&playerId=999') {
    const nodes = Object.fromEntries(['shareError', 'shareRetry', 'shareResult', 'shareBack'].map(id => [id, {hidden: true, textContent: ''}]));
    let click;
    nodes.shareRetry.addEventListener = (_, callback) => { click = callback; };
    const link = {};
    const facebook = [{}, {}], twitter = [{}, {}];
    for (const node of [...Object.values(nodes), link]) Object.defineProperty(node, 'innerHTML', {set() { assert.fail('unsafe HTML'); }});
    const doc = {getElementById: id => nodes[id], querySelector: () => link,
        querySelectorAll: selector => selector.includes('Facebook') ? facebook : twitter};
    const context = {fetch, URLSearchParams, URL}; vm.runInNewContext(code, context);
    const promise = context.bindCreateShare(doc, {url: '/airsonic/rest/createWebShare', query, csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token'});
    return {nodes, link, facebook, twitter, promise, retry: () => click()};
}
test('posts selection with CSRF and safely renders direct and encoded social URLs', async () => {
    let sent;
    const p = setup(async (url, options) => { sent = {url, options}; return ok; }); await p.promise;
    assert.equal(sent.url, '/airsonic/rest/createWebShare');
    assert.equal(sent.options.method, 'POST'); assert.equal(sent.options.credentials, 'same-origin');
    assert.equal(sent.options.cache, 'no-store'); assert.equal(sent.options.headers['X-CSRF-TOKEN'], 'token');
    const fields = new URLSearchParams(sent.options.body);
    assert.deepEqual(fields.getAll('i'), ['1', '0']); assert.equal(fields.get('id'), '42');
    assert.equal(fields.get('f'), 'json'); assert.equal(fields.get('c'), 'airsonic-web');
    assert.equal(fields.has('u'), false); assert.equal(fields.has('playerId'), false);
    assert.equal(p.nodes.shareResult.hidden, false); assert.equal(p.nodes.shareRetry.hidden, true);
    assert.equal(p.link.href, p.link.textContent); assert.equal(p.link.rel, 'noopener noreferrer');
    assert.equal(new URL(p.facebook[0].href).searchParams.get('u'), p.link.href);
    assert.equal(new URL(p.twitter[0].href).searchParams.get('status'), 'Listening to ' + p.link.href);
    assert.equal(p.nodes.shareBack.href, 'main.view?id=42');
});
test('suppresses duplicate creation while pending and after success, permits retry on REST failure', async () => {
    let complete, count = 0;
    const p = setup(() => { count++; return new Promise(resolve => { complete = resolve; }); }, '?playlist=8');
    await p.retry(); assert.equal(count, 1);
    complete({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {message: '<img onerror=x>'}}})});
    await p.promise; assert.equal(p.nodes.shareError.textContent, '<img onerror=x>'); assert.equal(p.nodes.shareRetry.hidden, false);
    const second = p.retry(); complete(ok); await second;
    await p.retry(); assert.equal(count, 2); assert.equal(p.nodes.shareResult.hidden, false);
    assert.equal(p.nodes.shareBack.href, 'playlist.view?id=8');
});
test('HTTP, redirects, malformed data, unsafe URL and network errors keep retry available', async () => {
    for (const response of [{ok: false, status: 403}, {...ok, redirected: true}, {ok: true, json: async () => ({})},
        {ok: true, json: async () => ({'subsonic-response': {status: 'ok', shares: {share: [{url: 'javascript:alert(1)'}]}}})},
        {ok: true, json: async () => { throw new Error('JSON'); }}, new Error('Offline')]) {
        const p = setup(async () => { if (response instanceof Error) throw response; return response; }, '?player=3&i=0');
        await p.promise; assert.equal(p.nodes.shareResult.hidden, true); assert.equal(p.nodes.shareRetry.hidden, false);
        assert.ok(p.nodes.shareError.textContent);
    }
});
