const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const source = readFileSync('airsonic-main/src/main/webapp/script/subsonic-playlists.js', 'utf8');
const ok = body => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok', ...body}})});
const tick = () => new Promise(resolve => setImmediate(resolve));
function setup(fetch) {
    let errors = 0, hide, timer, delay, cleared = false;
    const context = {fetch, URLSearchParams, window: {addEventListener(type, callback) {
        assert.equal(type, 'pagehide'); hide = callback;
    }, setTimeout(callback, ms) { timer = callback; delay = ms; return 1; },
    clearTimeout() { cleared = true; timer = null; }}};
    vm.runInNewContext(source, context);
    return {api: context.createSubsonicPlaylists({baseUrl: '/airsonic/rest/', csrfHeader: 'X-CSRF-TOKEN',
        csrfToken: 'session-token', onError() { errors++; }}), errors: () => errors, hide: () => hide(),
        fire: () => { const callback = timer; timer = null; callback(); }, delay: () => delay, cleared: () => cleared};
}

test('all playlist writes use session REST with CSRF and repeated parameters', async () => {
    const calls = [], successes = [];
    const fixture = setup(async (url, options) => { calls.push({url, options}); return ok({playlist: {id: '17'}}); });
    await fixture.api.create('empty', null, id => successes.push(id));
    await fixture.api.create('starred', null);
    await fixture.api.create('playQueue', 4);
    await fixture.api.update(17, 'Плейлист & <script>', '', false);
    await fixture.api.append(17, [8, 8, 9], id => successes.push(id));
    await fixture.api.remove(17, [2, 0]);
    await fixture.api.reorder(17, [2, 0, 1]);
    await fixture.api.delete(17);
    assert.deepEqual(successes, ['17', 17]);
    assert.deepEqual(calls.map(call => call.url.split('/').at(-1)),
        ['createPlaylist', 'createPlaylist', 'createPlaylist', 'updatePlaylist', 'updatePlaylist', 'updatePlaylist', 'updatePlaylist', 'deletePlaylist']);
    for (const {options} of calls) {
        assert.equal(options.method, 'POST');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.headers['X-CSRF-TOKEN'], 'session-token');
        assert.equal(options.body.get('f'), 'json');
        assert.equal(options.body.get('c'), 'airsonic-web');
        assert.equal(options.body.has('u'), false);
        assert.equal(options.body.has('p'), false);
    }
    assert.equal(calls[2].options.body.get('playerId'), '4');
    assert.equal(calls[3].options.body.get('name'), 'Плейлист & <script>');
    assert.equal(calls[3].options.body.get('comment'), '');
    assert.equal(calls[3].options.body.get('public'), 'false');
    assert.deepEqual(calls[4].options.body.getAll('songIdToAdd'), ['8', '8', '9']);
    assert.equal(calls[4].options.body.get('includeUnavailable'), 'true');
    assert.deepEqual(calls[5].options.body.getAll('songIndexToRemove'), ['2', '0']);
    assert.deepEqual(calls[6].options.body.getAll('songIndexOrder'), ['2', '0', '1']);
    assert.equal(fixture.errors(), 0);
});

test('maps REST lists and detailed entries to table fields without losing repeated songs', async () => {
    const calls = [];
    const fixture = setup(async (url, options) => {
        calls.push(options.body);
        const value = {id: '17', owner: 'admin', public: true, name: 'Name', songCount: 2};
        if (url.endsWith('getPlaylists')) return ok({playlists: {playlist: [value]}});
        return ok({playlist: {...value, entry: [
            {id: '8', track: 3, present: true, starred: 'date', playlistIndex: 0, suffix: 'mp3', formattedBitRate: 'vbr 192 Kbps', formattedFileSize: '3 MB'},
            {id: '8', present: false, playlistIndex: 1}
        ]}});
    });
    let lists, metadata, songs;
    await fixture.api.list(true, values => { lists = values; });
    await fixture.api.get(17, (value, entries) => { metadata = value; songs = entries; });
    assert.equal(calls[0].get('writableOnly'), 'true');
    assert.equal(calls[1].get('includeEntryDetails'), 'true');
    assert.equal(lists[0].username, 'admin');
    assert.equal(metadata.shared, true);
    assert.equal(metadata.fileCount, 2);
    assert.equal(metadata.comment, '');
    assert.equal(songs[0].starred, true);
    assert.equal(songs[0].trackNumber, 3);
    assert.equal(songs[0].fileSize, '3 MB');
    assert.equal(songs[0].bitRate, 'vbr 192 Kbps');
    assert.equal(songs[1].starred, false);
    assert.equal(songs[1].present, false);
    assert.equal(songs[1].playlistIndex, 1);
    assert.equal(songs[1].albumArtist, null);
});

test('serializes writes and subsequent refreshes', async () => {
    let release;
    const calls = [];
    const fixture = setup(async url => {
        calls.push(url);
        if (calls.length === 1) return new Promise(resolve => { release = () => resolve(ok({})); });
        return ok({playlists: {playlist: []}});
    });
    const first = fixture.api.append(17, [8]);
    const second = fixture.api.list(false, () => {});
    await tick();
    assert.equal(calls.length, 1);
    release();
    await Promise.all([first, second]);
    assert.equal(calls.length, 2);
});

for (const [name, fail] of Object.entries({
    http: () => ({ok: false}), redirect: () => ({ok: true, redirected: true}),
    network: () => { throw new Error('offline'); },
    json: () => ({ok: true, json: async () => { throw new Error('html'); }}),
    protocol: () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {code: 50}}})}),
    envelope: () => ({ok: true, json: async () => ({})})
})) {
    test(`${name} failure reports an error, suppresses success and allows retry`, async () => {
        let failed = true, callbacks = 0;
        const fixture = setup(async () => failed ? fail() : ok({}));
        await fixture.api.append(17, [8], () => callbacks++);
        assert.equal(fixture.errors(), 1);
        assert.equal(callbacks, 0);
        failed = false;
        await fixture.api.append(17, [8], () => callbacks++);
        assert.equal(callbacks, 1);
    });
}

test('failed reorder requests reload the authoritative order', async () => {
    let refreshes = 0;
    const fixture = setup(async () => ({ok: false}));
    await fixture.api.reorder(17, [1, 0], () => refreshes++);
    assert.equal(refreshes, 1);
    assert.equal(fixture.errors(), 1);
});

test('deleted playlist responses invoke the missing callback', async () => {
    let missing = 0;
    const fixture = setup(async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {code: 70}}})}));
    await fixture.api.get(17, () => assert.fail('unexpected success'), () => missing++);
    assert.equal(missing, 1);
    assert.equal(fixture.errors(), 0);
});

test('polls lists every 20 seconds, applies only changes and stops on pagehide', async () => {
    let reads = 0, callbacks = 0;
    const fixture = setup(async () => { reads++; return ok({playlists: {playlist: reads < 3 ? [] : [{id: '17'}]}}); });
    fixture.api.watchList(() => callbacks++);
    await tick();
    assert.equal(reads, 1); assert.equal(callbacks, 1); assert.equal(fixture.delay(), 20000);
    fixture.fire(); await tick();
    assert.equal(reads, 2); assert.equal(callbacks, 1);
    fixture.fire(); await tick();
    assert.equal(reads, 3); assert.equal(callbacks, 2);
    fixture.hide(); assert.equal(fixture.cleared(), true);
});

test('pagehide suppresses pending detail callbacks and prevents rescheduling', async () => {
    let release;
    const fixture = setup(() => new Promise(resolve => { release = () => resolve(ok({playlist: {id: '17'}})); }));
    fixture.api.watchPlaylist(17, () => assert.fail('disposed'), () => assert.fail('deleted'));
    await tick(); fixture.hide(); release(); await tick();
    assert.equal(fixture.delay(), undefined);
});

for (const code of [70, 50]) test(`polling reports an unavailable playlist (${code})`, async () => {
    let reads = 0;
    const fixture = setup(async () => ++reads === 1 ? ok({playlist: {id: '17'}}) :
        ({ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {code}}})}));
    const deleted = [];
    fixture.api.watchPlaylist(17, () => {}, id => deleted.push(id));
    await tick(); fixture.fire(); await tick();
    assert.deepEqual(deleted, [17]);
});

test('polling retries after a failed request', async () => {
    let reads = 0, callbacks = 0;
    const fixture = setup(async () => ++reads === 1 ? {ok: false} : ok({playlists: {playlist: []}}));
    fixture.api.watchList(() => callbacks++);
    await tick(); assert.equal(fixture.errors(), 1); assert.equal(fixture.delay(), 20000);
    fixture.fire(); await tick(); assert.equal(callbacks, 1);
});

test('a local write invalidates the previous polling snapshot', async () => {
    let callbacks = 0;
    const fixture = setup(async url => url.endsWith('getPlaylists') ? ok({playlists: {playlist: []}}) : ok({}));
    fixture.api.watchList(() => callbacks++);
    await tick();
    await fixture.api.delete(17);
    fixture.fire(); await tick();
    assert.equal(callbacks, 2);
});

test('all six JSP consumers include REST client, have valid scripts and no removed WS endpoints', () => {
    for (const page of ['left', 'playlists', 'playlist', 'mediaMain', 'playQueue', 'starred']) {
        const jsp = readFileSync(`airsonic-main/src/main/webapp/WEB-INF/jsp/${page}.jsp`, 'utf8');
        assert.ok(jsp.includes('include file="subsonicPlaylists.jsp"'), page);
        assert.ok(!jsp.includes('/app/playlists/'), page);
        assert.ok(!/\/user\/queue\/playlists\/(create|files|writable)/.test(jsp), page);
        for (const match of jsp.replace(/<c:url\b[^>]*\/>/g, '/url').matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) {
            const js = match[1].replace(/<%[\s\S]*?%>/g, '')
                .replace(/<fmt:message\b[^>]*\/>/g, 'message')
                .replace(/<spring:theme\b[^>]*\/>/g, 'theme')
                .replace(/<c:url\b[^>]*\/>/g, '/url')
                .replace(/<\/?c:[^>]*>/g, '')
                .replace(/\$\{paginationaddition\}/g, '')
                .replace(/\$\{[^}]*\}/g, '0');
            new vm.Script(js, {filename: page + '.jsp'});
        }
    }
});

test('play queue REST callbacks retain the player instance', () => {
    const jsp = readFileSync('airsonic-main/src/main/webapp/WEB-INF/jsp/playQueue.jsp', 'utf8');
    const methods = ['onSavePlaylist', 'onAppendPlaylist', 'appendPlaylist'].map(name => {
        const match = jsp.match(new RegExp('        ' + name + '\\([^)]*\\) \\{[\\s\\S]*?\\n        \\},'));
        assert.ok(match, name);
        return match[0].replace(/<fmt:message\b[^>]*\/>/g, 'success');
    }).join('\n');
    const updates = [], selections = [], calls = [];
    const context = {playlistApi: {
        create(source, playerId, callback) { calls.push([source, playerId]); callback(17); },
        list(writable, callback) { calls.push(writable); callback(['writable']); },
        append(id, ids, callback) { calls.push([id, ids]); callback(id); }
    }};
    const player = vm.runInNewContext('({' + methods + '})', context);
    player.player = {id: 4};
    player.playlistUpdatedCallback = id => updates.push(id);
    player.playlistSelectionCallback = values => selections.push(values);
    player.onSavePlaylist();
    player.onAppendPlaylist();
    player.appendPlaylist(18, [8, 9]);
    assert.deepEqual(updates, [17, 18]);
    assert.deepEqual(selections, [['writable']]);
    assert.equal(player.awaitingAppendPlaylistRequest, true);
    assert.deepEqual(calls, [['playQueue', 4], true, [18, [8, 9]]]);
});

test('playlist edits translate selected table rows to occurrence indices', () => {
    const jsp = readFileSync('airsonic-main/src/main/webapp/WEB-INF/jsp/playlist.jsp', 'utf8');
    const methods = ['onRemove', 'onRearrange'].map(name => {
        const match = jsp.match(new RegExp('function ' + name + '\\([^)]*\\) \\{[\\s\\S]*?\\n        \\}'));
        return match[0];
    }).join('\n');
    const calls = [], refresh = () => {};
    const context = {playlistId: 17, songs: [{id: 8, playlistIndex: 0}, {id: 8, playlistIndex: 1}, {id: 9, playlistIndex: 2}],
        updatePlaylistEntries: refresh, playlistApi: {
            remove(id, indices, callback) { calls.push([id, Array.from(indices), callback]); },
            reorder(id, indices, callback) { calls.push([id, Array.from(indices), callback]); }
        }};
    vm.runInNewContext(methods, context);
    context.onRemove([1]);
    context.onRearrange([2, 0, 1]);
    assert.deepEqual(calls, [[17, [1], refresh], [17, [2, 0, 1], refresh]]);
});


test('playlist tables receive all columns when REST omits optional fields', async () => {
    const fixture = setup(async url => url.endsWith('getPlaylists')
        ? ok({playlists: {playlist: [{id: '1'}]}})
        : ok({playlist: {id: '1', entry: [{id: '2'}]}}));
    let lists, songs;
    await fixture.api.list(false, value => { lists = value; });
    await fixture.api.get(1, (playlist, entries) => { songs = entries; });
    for (const field of ['id', 'name', 'comment', 'shared', 'fileCount', 'duration', 'username', 'created', 'changed', 'importedFrom'])
        assert.ok(Object.hasOwn(lists[0], field), field);
    for (const field of ['trackNumber', 'discNumber', 'title', 'album', 'artist', 'albumArtist', 'genre', 'year', 'format', 'fileSize', 'duration', 'bitRate', 'playCount', 'lastPlayed', 'lastScanned', 'created', 'changed', 'starred', 'present'])
        assert.ok(Object.hasOwn(songs[0], field), field);
    assert.equal(lists[0].importedFrom, null);
    assert.equal(songs[0].discNumber, null);
    assert.match(readFileSync('airsonic-main/src/main/webapp/WEB-INF/jsp/subsonicPlaylists.jsp', 'utf8'), /subsonic-playlists\.js\?v=3/);
});
