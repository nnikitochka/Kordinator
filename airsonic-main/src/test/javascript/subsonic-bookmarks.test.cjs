// Run with: node airsonic-main/src/test/javascript/subsonic-bookmarks.test.cjs
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const root = 'airsonic-main/src/main/webapp/';
const ok = (data = {}) => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok', ...data}})});
function setup(fetch, window = {}) {
    const context = {fetch, URLSearchParams, Promise, window};
    vm.runInNewContext(readFileSync(root + 'script/subsonic-bookmarks.js', 'utf8'), context);
    let errors = 0;
    const api = context.createSubsonicBookmarks({
        listUrl: '/rest/getBookmarks', createUrl: '/rest/createBookmark', deleteUrl: '/rest/deleteBookmark',
        csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'csrf', onError: () => errors++
    });
    return {api, errors: () => errors};
}

test('CRUD uses Subsonic parameter names, browser session, CSRF and encoded comments', async () => {
    const calls = [];
    const {api} = setup(async (url, options) => { calls.push({url, options}); return ok(); });
    await api.create(42, 123456, 'Позиция & <bookmark>');
    await api.list(() => {});
    await api.remove(42);
    assert.deepEqual(calls.map(c => c.url), ['/rest/createBookmark', '/rest/getBookmarks', '/rest/deleteBookmark']);
    for (const {options} of calls) {
        assert.equal(options.method, 'POST');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf');
        assert.equal(options.body.get('f'), 'json');
        assert.equal(options.body.get('c'), 'airsonic-web');
        assert.equal(options.body.has('p'), false);
    }
    const form = new URLSearchParams(calls[0].options.body.toString());
    assert.equal(form.get('id'), '42');
    assert.equal(form.get('position'), '123456');
    assert.equal(form.get('comment'), 'Позиция & <bookmark>');
    assert.equal(form.has('positionMillis'), false);
    assert.equal(calls[1].options.body.get('includeEntryAvailability'), 'true');
});

test('REST entries retain playback offsets, metadata, availability and starred state for the website', async () => {
    const bookmark = {position: 100123, comment: 'note', created: '2026-01-01', changed: '2026-01-02',
        entryPresent: true, entry: {id: '12', parent: '9', title: 'Song', album: 'Album', artist: 'Artist',
            duration: 200, starred: '2026-01-01'}};
    const {api} = setup(async () => ok({bookmarks: {bookmark: [bookmark,
        {...bookmark, entryPresent: false, entry: {id: '13', title: 'Offline'}}]}}));
    let result;
    await api.list(b => { result = b; });
    assert.equal(result['12'].positionMillis, 100123);
    assert.equal(result['12'].mediaFileEntry.starred, true);
    assert.equal(result['12'].mediaFileEntry.present, true);
    assert.equal(result['12'].mediaFileEntry.parent, '9');
    assert.equal(result['12'].mediaFileEntry.duration, 200);
    assert.equal(result['12'].comment, 'note');
    assert.equal(result['12'].created, '2026-01-01');
    assert.equal(result['12'].changed, '2026-01-02');
    assert.equal(result['13'].mediaFileEntry.starred, false);
    assert.equal(result['13'].mediaFileEntry.present, false);
});

test('empty or missing entries replace stale website bookmarks with an empty map', async () => {
    for (const data of [{}, {bookmarks: {}}, {bookmarks: {bookmark: [{}]}}]) {
        const {api} = setup(async () => ok(data));
        await api.list(b => assert.equal(Object.keys(b).length, 0));
    }
});

test('automatic progress writes and polling reads run in order', async () => {
    let release;
    const firstResponse = new Promise(resolve => { release = resolve; });
    const calls = [];
    const {api} = setup(async (url, options) => {
        calls.push(url + ':' + options.body.get('position'));
        return calls.length === 1 ? firstResponse : ok();
    });
    const first = api.create(1, 100, 'audio');
    const second = api.create(1, 200, 'audio');
    const third = api.list(() => {});
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(calls.length, 1);
    release(ok());
    await Promise.all([first, second, third]);
    assert.deepEqual(calls, ['/rest/createBookmark:100', '/rest/createBookmark:200', '/rest/getBookmarks:null']);
});

for (const [name, fail] of Object.entries({
    http: async () => ({ok: false}),
    redirect: async () => ({ok: true, redirected: true}),
    network: async () => { throw Error('offline'); },
    json: async () => ({ok: true, json: async () => { throw Error('login page'); }}),
    protocol: async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed'}})}),
    envelope: async () => ({ok: true, json: async () => ({})})
})) {
    test(`${name} failure preserves UI state and allows the next operation`, async () => {
        let failing = true, callbacks = 0;
        const {api, errors} = setup((...args) => failing ? fail(...args) : Promise.resolve(ok()));
        await api.remove(42, () => callbacks++);
        assert.equal(callbacks, 0);
        assert.equal(errors(), 1);
        failing = false;
        await api.create(42, 0, '', () => callbacks++);
        assert.equal(callbacks, 1);
    });
}

function pageFunction(page, signature) {
    const source = readFileSync(root + `WEB-INF/jsp/${page}.jsp`, 'utf8');
    const start = source.indexOf(signature);
    assert.notEqual(start, -1);
    const body = source.indexOf('{', start);
    let depth = 1, end = body + 1;
    while (depth && end < source.length) {
        if (source[end] === '{') depth++;
        if (source[end] === '}') depth--;
        end++;
    }
    return source.slice(start, end);
}

test('bookmark table deletes by media ID and reloads only after REST success', () => {
    let request, reads = 0;
    const context = {
        bookmarkApi: {remove: (id, success) => { request = {id, success}; }, list: () => reads++},
        bookmarksTable: {row: () => ({data: () => ({mediaFileEntry: {id: 42}})})},
        getBookmarksCallback() {}
    };
    vm.runInNewContext(pageFunction('bookmarks', 'function onRemoveBookmark('), context);
    context.onRemoveBookmark(5);
    assert.equal(request.id, 42);
    assert.equal(reads, 0);
    request.success();
    assert.equal(reads, 1);
});

test('audio player autosaves milliseconds through REST and can disable autosaving', () => {
    const calls = [];
    const context = {bookmarkApi: {create: (...args) => calls.push(args)}};
    const fn = vm.runInNewContext('({' + pageFunction('playQueue', 'setBookmark() {') + '}).setBookmark', context);
    const player = {autoBookmark: true, songs: [{id: 42}], currentSongIndex: 0, audioPlayer: {currentTime: 12.345}, player: {id: 7}};
    fn.call(player);
    assert.deepEqual(calls, [[42, 12345, 'Played on Web Player 7']]);
    player.autoBookmark = false;
    fn.call(player);
    assert.equal(calls.length, 1);
});

test('video player keeps bookmark interval, milliseconds and duplicate suppression', () => {
    const calls = [];
    const context = {bookmarkApi: {create: (...args) => calls.push(args)}, videoPlayer: {currentTime: 10},
        videoModel: {autoBookmark: true, position: 0, videoBookmarkFrequency: 5, videoId: '42'}};
    vm.runInNewContext(pageFunction('videoPlayer', 'function setBookmark()'), context);
    context.setBookmark();
    context.setBookmark();
    assert.deepEqual(calls, [['42', 10000, 'Played on Web Video Player']]);
    context.videoPlayer.currentTime = 11;
    context.setBookmark();
    assert.equal(calls.length, 1);
});

function pollingWindow() {
    let timer, leave, cleared = false;
    return {
        window: {
            addEventListener: (event, callback) => { assert.equal(event, 'pagehide'); leave = callback; },
            setTimeout: (callback, delay) => { assert.equal(delay, 20000); timer = callback; return 1; },
            clearTimeout: () => { cleared = true; timer = undefined; }
        },
        tick: async () => { assert.ok(timer); const callback = timer; timer = undefined; callback(); await flush(); },
        leave: () => leave(), hasTimer: () => !!timer, cleared: () => cleared
    };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('polls immediately and every 20 seconds, updating only when bookmarks change', async () => {
    let reads = 0, position = 100, updates = [];
    const clock = pollingWindow();
    const {api} = setup(async () => { reads++; return ok({bookmarks: {bookmark: [
        {position, entry: {id: '1'}}]}}); }, clock.window);
    api.watch(value => updates.push(value['1'].positionMillis));
    await flush();
    assert.equal(reads, 1);
    assert.deepEqual(updates, [100]);
    await clock.tick();
    assert.equal(reads, 2);
    assert.deepEqual(updates, [100]);
    position = 200;
    await clock.tick();
    assert.deepEqual(updates, [100, 200]);
    clock.leave();
    assert.equal(clock.cleared(), true);
    assert.equal(clock.hasTimer(), false);
});

test('polling resumes after errors and local writes invalidate the previous snapshot', async () => {
    let fail = false, updates = 0;
    const clock = pollingWindow();
    const {api, errors} = setup(async () => { if (fail) throw Error('offline'); return ok(); }, clock.window);
    api.watch(() => updates++);
    await flush();
    fail = true;
    await clock.tick();
    assert.equal(errors(), 1);
    assert.equal(updates, 1);
    fail = false;
    await api.create(1, 100, 'audio');
    await clock.tick();
    assert.equal(updates, 2);
});

test('next poll is scheduled only after the preceding request finishes', async () => {
    let resolve, reads = 0;
    const response = new Promise(done => { resolve = done; });
    const clock = pollingWindow();
    const {api} = setup(() => { reads++; return response; }, clock.window);
    api.watch(() => {});
    await flush();
    assert.equal(reads, 1);
    assert.equal(clock.hasTimer(), false);
    resolve(ok());
    await flush();
    assert.equal(clock.hasTimer(), true);
    await clock.tick();
    assert.equal(reads, 2);
    clock.leave();
});

test('an in-flight REST read cannot update or reschedule after leaving the page', async () => {
    let resolve;
    const response = new Promise(done => { resolve = done; });
    const clock = pollingWindow();
    const {api} = setup(() => response, clock.window);
    api.watch(() => assert.fail('obsolete UI update'));
    await flush();
    clock.leave();
    resolve(ok());
    await flush();
    assert.equal(clock.hasTimer(), false);
});

test('audio player resumes at the REST bookmark position', () => {
    const context = {};
    const fn = vm.runInNewContext('({' + pageFunction('playQueue', 'onSkip(index, offset, nobookmarks)') + '}).onSkip', context);
    let skipped;
    const player = {songs: [{id: 42}], bookmarks: {'42': {positionMillis: 123456}}, player: {tech: 'WEB'},
        playQueueSkipCallback: value => { skipped = value; }};
    fn.call(player, 0);
    assert.equal(skipped.offset, 123456);
    fn.call(player, 0, 5000);
    assert.equal(skipped.offset, 5000);
});

test('all bookmark pages include the REST helper and have no internal bookmark API calls', () => {
    for (const page of ['bookmarks', 'playQueue', 'videoPlayer']) {
        const source = readFileSync(root + `WEB-INF/jsp/${page}.jsp`, 'utf8');
        assert.ok(source.includes('include file="subsonicBookmarks.jsp"'));
        assert.ok(!source.includes('/app/bookmarks/'));
        assert.ok(!source.includes('/user/queue/bookmarks/get'));
    }
});


test('bookmark rows include every optional table field and preserve values', async () => {
    const {api} = setup(async () => ok({bookmarks: {bookmark: [{position: 0, entry: {id: '12', title: 'Song'}}]}}));
    let result; await api.list(value => { result = value['12']; });
    for (const name of ['id', 'positionMillis', 'comment', 'created', 'changed']) assert.ok(Object.hasOwn(result, name), name);
    for (const name of ['title', 'album', 'artist', 'duration', 'starred', 'present']) assert.ok(Object.hasOwn(result.mediaFileEntry, name), name);
    assert.equal(result.positionMillis, 0); assert.equal(result.mediaFileEntry.title, 'Song');
    assert.equal(result.mediaFileEntry.album, null); assert.equal(result.comment, null);
    assert.match(readFileSync(root + 'WEB-INF/jsp/subsonicBookmarks.jsp', 'utf8'), /subsonic-bookmarks\.js\?v=3/);
});
