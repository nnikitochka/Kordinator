const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('airsonic-main/src/main/webapp/script/file-upload.js', 'utf8');
function setup(fetch) {
    const button = {disabled: false};
    const rows = [];
    const results = {appendChild: row => rows.push(row.textContent)};
    Object.defineProperty(results, 'textContent', {set() { rows.length = 0; }});
    Object.defineProperty(results, 'innerHTML', {set() { assert.fail('unsafe result rendering'); }});
    let submit;
    const form = {querySelector: () => button, addEventListener: (name, fn) => { assert.equal(name, 'submit'); submit = fn; }};
    const data = {files: ['one.mp3', 'two.zip'], dir: '/music', unzip: true};
    const context = {fetch, FormData: function (f) { assert.equal(f, form); return data; },
        document: {createElement: () => { const row = {}; Object.defineProperty(row, 'innerHTML', {set() { assert.fail('unsafe row'); }}); return row; }}};
    vm.runInNewContext(code, context);
    context.bindFileUpload(form, results, {url: '/airsonic/rest/upload', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token', uploaded: 'Uploaded {file}', extracted: 'Extracted {file}'});
    return {button, rows, data, submit: () => submit({preventDefault() {}})};
}
const response = body => ({ok: true, json: async () => ({'subsonic-response': body})});
test('posts the complete multipart form with CSRF and displays uploaded and extracted files safely', async () => {
    let request;
    const page = setup(async (url, options) => { request = {url, options}; return response({status: 'ok', uploadResult: {uploadedFile: ['Музыка $&.mp3'], unzippedFile: ['<img src=x>.mp3']}}); });
    await page.submit();
    assert.equal(request.url, '/airsonic/rest/upload?f=json&c=airsonic-web');
    assert.equal(request.options.method, 'POST'); assert.equal(request.options.body, page.data);
    assert.equal(request.options.headers['X-CSRF-TOKEN'], 'token'); assert.equal(request.options.headers['Content-Type'], undefined);
    assert.equal(request.options.credentials, 'same-origin'); assert.equal(request.options.cache, 'no-store');
    assert.deepEqual(page.rows, ['Uploaded Музыка $&.mp3', 'Extracted <img src=x>.mp3']);
    assert.equal(page.button.disabled, false);
});
test('shows partial success together with errors without interpreting markup', async () => {
    const page = setup(async () => response({status: 'failed', uploadResult: {uploadedFile: ['good.mp3'], error: ['<script>bad</script>']}, error: {message: 'Some files failed'}}));
    await page.submit(); assert.deepEqual(page.rows, ['Uploaded good.mp3', '<script>bad</script>', 'Some files failed']);
    assert.equal(page.button.disabled, false);
});
test('prevents duplicate submissions and permits retry while clearing previous results', async () => {
    let finish, requests = 0;
    const page = setup(() => { requests++; return new Promise(resolve => { finish = resolve; }); });
    const first = page.submit(); assert.equal(page.button.disabled, true); await page.submit(); assert.equal(requests, 1);
    finish(response({status: 'failed', error: {message: 'Try again'}})); await first;
    assert.deepEqual(page.rows, ['Try again']); assert.equal(page.button.disabled, false);
    const retry = page.submit(); assert.deepEqual(page.rows, []); assert.equal(requests, 2);
    finish(response({status: 'ok', uploadResult: {}})); await retry; assert.equal(page.button.disabled, false);
});
test('handles HTTP errors, redirects, malformed JSON and network failures without leaving the form disabled', async () => {
    for (const next of [{ok: false}, {...response({status: 'ok'}), redirected: true}, {ok: true, json: async () => ({})},
        response({status: 'ok'}), {ok: true, json: async () => { throw new Error('Invalid JSON'); }}, new Error('Offline')]) {
        const page = setup(async () => { if (next instanceof Error) throw next; return next; });
        await page.submit(); assert.equal(page.rows.length, 1); assert.ok(page.rows[0]); assert.equal(page.button.disabled, false);
    }
});
