// Run with: node airsonic-main/src/test/javascript/subsonic-stars.test.cjs
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');

function setup(fetch) {
    const context = {Promise, URLSearchParams, fetch};
    vm.runInNewContext(readFileSync('airsonic-main/src/main/webapp/script/subsonic-stars.js', 'utf8'), context);
    let errors = 0;
    const setStarred = context.createSubsonicStars({
        starUrl: '/airsonic/rest/star', unstarUrl: '/airsonic/rest/unstar',
        csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'session-csrf', onError: () => errors++
    });
    return {setStarred, errors: () => errors};
}
const ok = () => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok'}})});

test('single and bulk requests use session, CSRF, and repeated Subsonic id parameters', async () => {
    const requests = [];
    const {setStarred, errors} = setup(async (url, options) => {
        requests.push({url, options});
        return ok();
    });
    let updated = 0;
    await setStarred([12, 34], true, () => updated++);
    await setStarred([12], false, () => updated++);
    assert.equal(updated, 2);
    assert.equal(errors(), 0);
    assert.deepEqual(requests.map(r => r.url), ['/airsonic/rest/star', '/airsonic/rest/unstar']);
    const options = requests[0].options;
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers['X-CSRF-TOKEN'], 'session-csrf');
    assert.deepEqual(options.body.getAll('id'), ['12', '34']);
    assert.equal(options.body.get('f'), 'json');
    assert.equal(options.body.get('c'), 'airsonic-web');
    assert.equal(options.body.has('p'), false);
});

test('UI callback waits for the server; overlapping writes execute in order', async () => {
    let release;
    const response = new Promise(resolve => { release = resolve; });
    const calls = [];
    const {setStarred} = setup(async url => {
        calls.push(url);
        return calls.length === 1 ? response : ok();
    });
    let updated = false;
    const first = setStarred([1], true, () => { updated = true; });
    const second = setStarred([1], false);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(updated, false);
    assert.equal(calls.length, 1);
    release(ok());
    await Promise.all([first, second]);
    assert.equal(updated, true);
    assert.deepEqual(calls, ['/airsonic/rest/star', '/airsonic/rest/unstar']);
});

for (const [name, fetch] of Object.entries({
    http: async () => ({ok: false}),
    redirect: async () => ({ok: true, redirected: true}),
    network: async () => { throw new Error('offline'); },
    json: async () => ({ok: true, json: async () => { throw new Error('HTML login page'); }}),
    protocol: async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {code: 70}}})}),
    envelope: async () => ({ok: true, json: async () => ({})})
})) {
    test(`${name} failure reports an error, leaves the UI unchanged, and permits retry`, async () => {
        let failed = true;
        const {setStarred, errors} = setup((...args) => failed ? fetch(...args) : Promise.resolve(ok()));
        let updated = 0;
        await setStarred([1], true, () => updated++);
        assert.equal(errors(), 1);
        assert.equal(updated, 0);
        failed = false;
        await setStarred([1], true, () => updated++);
        assert.equal(updated, 1);
    });
}

test('empty bulk selection sends no request', async () => {
    const {setStarred} = setup(() => { assert.fail('unexpected request'); });
    await setStarred([], true);
});

function pageFunction(page, signature) {
    const source = readFileSync(`airsonic-main/src/main/webapp/WEB-INF/jsp/${page}.jsp`, 'utf8');
    const start = source.indexOf(signature);
    assert.notEqual(start, -1);
    const body = source.indexOf('{', start);
    let depth = 1, end = body + 1;
    while (depth && end < source.length) {
        if (source[end] === '{') depth++;
        if (source[end] === '}') depth--;
        end++;
    }
    return source.slice(start, end).replace(/<spring:theme code=["']([^"']+)["']\/>/g, '$1');
}

// The video page's REST rendering and star actions are covered in video-player.test.cjs.
test('starred page image and label change only after REST success', () => {
    let request;
    const context = {
        starredConfig: {starOn: 'starOn', starOff: 'starOff', star: 'star', unstar: 'unstar'},
        document: {
            createElement: () => ({
                children: [], style: {},
                appendChild(child) { this.children.push(child); },
                addEventListener(event, callback) { this.click = callback; }
            })
        },
        setMediaFilesStarred: (ids, status, callback) => { request = {ids, status, callback}; }
    };
    vm.runInNewContext(pageFunction('starred', 'function actionCell('), context);
    const row = {cells: [], appendChild(cell) { this.cells.push(cell); }};
    const item = {id: 42, starredDate: null};
    context.actionCell(row, item, 'song', {streamAllowed: false, partyModeEnabled: false});

    const image = row.cells[0].children[0];
    assert.equal(image.src, 'starOff');
    image.click();
    assert.deepEqual(Array.from(request.ids), [42]);
    assert.equal(request.status, true);
    assert.equal(item.starredDate, null);
    assert.equal(image.src, 'starOff');
    request.callback();
    assert.notEqual(item.starredDate, null);
    assert.equal(image.src, 'starOn');
    assert.equal(image.title, 'unstar');

    image.click();
    assert.equal(request.status, false);
    request.callback();
    assert.equal(item.starredDate, null);
    assert.equal(image.src, 'starOff');
    assert.equal(image.title, 'star');
});

for (const page of ['playlist', 'bookmarks']) {
    test(`${page}: row data changes only on successful REST completion`, () => {
        const song = {id: 42, starred: false};
        let request, invalidated = 0;
        const table = {rows: () => ({invalidate: () => invalidated++}), row: () => ({data: () => ({mediaFileEntry: song})})};
        const context = {
            songs: [song], playlistMusicTable: table, bookmarksTable: table,
            setMediaFilesStarred: (ids, status, callback) => { request = {ids, status, callback}; }
        };
        vm.runInNewContext(pageFunction(page, 'function onStar(index)'), context);
        context.onStar(0);
        assert.equal(song.starred, false);
        assert.equal(invalidated, 0);
        assert.equal(request.ids[0], 42);
        request.callback();
        assert.equal(song.starred, true);
        assert.equal(invalidated, 1);
    });
}

test('media directory star state updates after REST success', () => {
    let request, updates = 0;
    const context = {
        mediaDir: {id: 42, starred: false}, updateStarImage: () => updates++,
        setMediaFilesStarred: (ids, status, callback) => { request = {ids, status, callback}; }
    };
    vm.runInNewContext(pageFunction('mediaMain', 'function toggleMediaDirStar('), context);
    context.toggleMediaDirStar(true);
    assert.equal(context.mediaDir.starred, false);
    assert.equal(updates, 0);
    request.callback();
    assert.equal(context.mediaDir.starred, true);
    assert.equal(updates, 1);
});

test('play queue bulk selection sends all IDs and updates only on success', () => {
    const songs = [{id: 4, starred: false}, {id: 8, starred: false}];
    let request, invalidated = 0;
    const context = {setMediaFilesStarred: (ids, status, callback) => { request = {ids, status, callback}; }};
    const handler = vm.runInNewContext('({' + pageFunction('playQueue', 'onStar(indices, status)') + '})', context);
    handler.songs = songs;
    handler.musicTable = {rows: () => ({invalidate: () => invalidated++})};
    handler.onStar([0, 1], true);
    assert.deepEqual(Array.from(request.ids), [4, 8]);
    assert.equal(songs[0].starred, false);
    assert.equal(songs[1].starred, false);
    request.callback();
    assert.equal(songs[0].starred, true);
    assert.equal(songs[1].starred, true);
    assert.equal(invalidated, 1);
});
