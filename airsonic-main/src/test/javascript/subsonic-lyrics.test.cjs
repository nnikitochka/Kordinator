// Run with: node airsonic-main/src/test/javascript/subsonic-lyrics.test.cjs
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const root = 'airsonic-main/src/main/webapp/';
const ok = (lyrics = {}) => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok', lyrics}})});
function setup(fetch) {
    const elements = Object.fromEntries(['wait', 'lyrics', 'noLyricsFound', 'tryLater', 'lyricsText', 'lyricsHeader']
        .map(id => [id, {style: {}, textContent: ''}]));
    for (const element of Object.values(elements)) {
        Object.defineProperty(element, 'innerHTML', {set() { assert.fail('Lyrics must be rendered as text'); }});
    }
    const context = {URLSearchParams, AbortController, fetch, document: {getElementById: id => elements[id]}};
    vm.runInNewContext(readFileSync(root + 'script/subsonic-lyrics.js', 'utf8'), context);
    const getLyrics = context.createSubsonicLyrics({url: '/airsonic/rest/getLyrics',
        csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'csrf'});
    return {getLyrics, elements};
}
function shown(elements, id) { return elements[id].style.display !== 'none'; }

test('search uses Subsonic title, encoded Unicode, session and CSRF', async () => {
    let call;
    const {getLyrics} = setup(async (url, options) => { call = {url, options}; return ok(); });
    await getLyrics('Artist & Co', 'Песня ?');
    assert.equal(call.url, '/airsonic/rest/getLyrics');
    assert.equal(call.options.method, 'POST');
    assert.equal(call.options.credentials, 'same-origin');
    assert.equal(call.options.headers['X-CSRF-TOKEN'], 'csrf');
    const form = new URLSearchParams(call.options.body.toString());
    assert.equal(form.get('artist'), 'Artist & Co');
    assert.equal(form.get('title'), 'Песня ?');
    assert.equal(form.get('f'), 'json');
    assert.equal(form.get('c'), 'airsonic-web');
    assert.equal(form.get('includeLookupStatus'), 'true');
    assert.equal(form.has('song'), false);
    assert.equal(form.has('p'), false);
});

test('shows loading until the response, then renders literal lyrics and newlines safely', async () => {
    let resolve;
    const response = new Promise(done => { resolve = done; });
    const {getLyrics, elements} = setup(() => response);
    const task = getLyrics('Artist', 'Title');
    assert.equal(shown(elements, 'wait'), true);
    for (const id of ['lyrics', 'noLyricsFound', 'tryLater']) assert.equal(shown(elements, id), false);
    const text = '<script>literal</script>\nSecond line';
    resolve(ok({artist: 'Found <artist>', title: 'Found title', value: text}));
    await task;
    assert.equal(elements.lyricsHeader.textContent, 'Found <artist> - Found title');
    assert.equal(elements.lyricsText.textContent, text);
    assert.equal(shown(elements, 'lyrics'), true);
    assert.equal(shown(elements, 'wait'), false);
    assert.equal(shown(elements, 'tryLater'), false);
});

test('empty and whitespace results show not-found and clear a previous result', async () => {
    for (const value of [undefined, null, '', ' \n ']) {
        let response = ok({value: 'previous text'});
        const {getLyrics, elements} = setup(() => response);
        await getLyrics('Artist', 'Title');
        response = ok({value});
        await getLyrics('Other', 'Missing');
        assert.equal(shown(elements, 'noLyricsFound'), true);
        assert.equal(shown(elements, 'lyrics'), false);
        assert.equal(shown(elements, 'tryLater'), false);
        assert.equal(elements.lyricsText.textContent, '');
        assert.equal(shown(elements, 'wait'), false);
    }
});

test('provider retry status shows try-later rather than not-found', async () => {
    const {getLyrics, elements} = setup(async () => ok({tryLater: true}));
    await getLyrics('Artist', 'Title');
    assert.equal(shown(elements, 'tryLater'), true);
    assert.equal(shown(elements, 'noLyricsFound'), false);
    assert.equal(shown(elements, 'wait'), false);
});

for (const [name, failure] of Object.entries({
    http: async () => ({ok: false}),
    redirect: async () => ({ok: true, redirected: true}),
    network: async () => { throw Error('offline'); },
    json: async () => ({ok: true, json: async () => { throw Error('HTML login page'); }}),
    protocol: async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed'}})}),
    envelope: async () => ({ok: true, json: async () => ({})}),
    missingLyrics: async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok'}})})
})) {
    test(`${name} error allows retry and never leaves the spinner running`, async () => {
        let fail = true;
        const {getLyrics, elements} = setup((...args) => fail ? failure(...args) : Promise.resolve(ok({value: 'found'})));
        await getLyrics('Artist', 'Title');
        assert.equal(shown(elements, 'tryLater'), true);
        assert.equal(shown(elements, 'wait'), false);
        assert.equal(shown(elements, 'noLyricsFound'), false);
        fail = false;
        await getLyrics('Artist', 'Title');
        assert.equal(shown(elements, 'tryLater'), false);
        assert.equal(shown(elements, 'lyrics'), true);
    });
}

test('a late previous response cannot replace a newer search or stop its spinner', async () => {
    const pending = [], signals = [];
    const {getLyrics, elements} = setup((url, options) => {
        signals.push(options.signal);
        return new Promise(resolve => pending.push(resolve));
    });
    const first = getLyrics('Old', 'Title');
    const second = getLyrics('New', 'Title');
    assert.equal(signals[0].aborted, true);
    pending[0](ok({value: 'old'}));
    await first;
    assert.equal(shown(elements, 'wait'), true);
    assert.equal(shown(elements, 'lyrics'), false);
    pending[1](ok({value: 'new'}));
    await second;
    assert.equal(elements.lyricsText.textContent, 'new');
    assert.equal(elements.lyricsHeader.textContent, 'New - Title');
    assert.equal(shown(elements, 'wait'), false);
});

test('an old failed request cannot display an error over a newer result', async () => {
    let reject;
    let calls = 0;
    const {getLyrics, elements} = setup(() => ++calls === 1
        ? new Promise((resolve, fail) => { reject = fail; }) : Promise.resolve(ok({value: 'latest'})));
    const first = getLyrics('Old', 'Title');
    await getLyrics('New', 'Title');
    reject(Error('aborted'));
    await first;
    assert.equal(shown(elements, 'tryLater'), false);
    assert.equal(shown(elements, 'lyrics'), true);
    assert.equal(elements.lyricsText.textContent, 'latest');
});

test('popup initializes search from input values without WebSocket or inline search-string interpolation', () => {
    const source = readFileSync(root + 'WEB-INF/jsp/lyrics.jsp', 'utf8');
    assert.equal(source.includes('StompClient'), false);
    assert.equal(source.includes('websocket.jsp'), false);
    assert.equal(source.includes('white-space: pre-wrap'), true);
    assert.equal(source.includes("value=\"<c:out value='${param.artist}'/>\""), true);
    assert.equal(source.includes("value=\"<c:out value='${param.song}'/>\""), true);
    assert.equal(source.includes("${model.artist}"), false);
    const context = {getLyrics: (artist, song) => { context.result = [artist, song]; },
        $: id => ({val: () => id === '#artist' ? "O'Brien" : '<Song>'})};
    const search = source.match(/function search\(\) \{[\s\S]*?\n        \}/)[0];
    const init = source.match(/function init\(\) \{[\s\S]*?\n        \}/)[0];
    vm.runInNewContext(search + '\n' + init, context);
    context.init();
    assert.deepEqual(context.result, ["O'Brien", '<Song>']);
});
