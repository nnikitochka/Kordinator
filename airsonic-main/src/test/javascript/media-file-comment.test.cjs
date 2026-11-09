const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('airsonic-main/src/main/webapp/script/media-file-comment.js', 'utf8');
const ok = {ok: true, json: async () => ({'subsonic-response': {status: 'ok'}})};
function setup(fetch) {
    let submit;
    const button = {disabled: false};
    const error = {textContent: ''};
    Object.defineProperty(error, 'innerHTML', {set() { assert.fail('unsafe HTML'); }});
    const form = {querySelector: () => button, addEventListener: (_, f) => { submit = f; }};
    const saved = [];
    const context = {fetch, URLSearchParams, FormData: function () { return [['id', '42'], ['comment', 'Тест & <script>x</script>\nline']]; }};
    vm.runInNewContext(code, context);
    context.bindMediaFileComment(form, error, {url: '/airsonic/rest/setMediaFileComment', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token', onSaved: (...args) => saved.push(args)});
    return {button, error, saved, submit: () => submit({preventDefault() {}})};
}
test('saves plain Unicode text with CSRF through REST and reports the submitted snapshot', async () => {
    let sent;
    const p = setup(async (url, options) => { sent = {url, options}; return ok; });
    await p.submit();
    assert.equal(sent.url, '/airsonic/rest/setMediaFileComment');
    assert.equal(sent.options.method, 'POST');
    assert.equal(sent.options.headers['X-CSRF-TOKEN'], 'token');
    assert.equal(sent.options.credentials, 'same-origin');
    assert.equal(sent.options.cache, 'no-store');
    const fields = new URLSearchParams(sent.options.body);
    assert.equal(fields.get('f'), 'json'); assert.equal(fields.get('c'), 'airsonic-web');
    assert.deepEqual(p.saved, [['42', fields.get('comment')]]);
    assert.equal(fields.get('comment'), 'Тест & <script>x</script>\nline');
    assert.equal(p.button.disabled, false);
});
test('prevents duplicate saves and allows a retry after REST errors rendered as text', async () => {
    let finish, count = 0;
    const p = setup(() => { count++; return new Promise(resolve => { finish = resolve; }); });
    const first = p.submit(); await p.submit(); assert.equal(count, 1); assert.equal(p.button.disabled, true);
    finish({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {message: '<img src=x>'}}})});
    await first; assert.equal(p.error.textContent, '<img src=x>'); assert.deepEqual(p.saved, []);
    const second = p.submit(); assert.equal(p.error.textContent, ''); finish(ok); await second;
    assert.equal(count, 2); assert.equal(p.saved.length, 1); assert.equal(p.button.disabled, false);
});
test('HTTP, redirect, network and malformed responses preserve editing and allow retry', async () => {
    for (const reply of [{ok: false}, {...ok, redirected: true}, {ok: true, json: async () => ({})},
        {ok: true, json: async () => { throw new Error('Invalid JSON'); }}, new Error('Offline')]) {
        const p = setup(async () => { if (reply instanceof Error) throw reply; return reply; });
        await p.submit(); assert.ok(p.error.textContent); assert.deepEqual(p.saved, []); assert.equal(p.button.disabled, false);
    }
});
