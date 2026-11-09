const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/WEB-INF/jsp/playQueue.jsp'), 'utf8');
const method = jsp.slice(jsp.indexOf('        playQueueCallback(playQueue, initial, suppressPlaylistDownload) {'), jsp.indexOf('        updateWindowTitle(song) {'));
const accept = jsp.slice(jsp.indexOf('        acceptQueue(queue, initial, suppressPlaylistDownload, polling) {'), jsp.indexOf('        // Player updates'));
function queuePage(tech) {
    const navigations = [];
    const ui = {text() { return this; }, html() { return this; }, column() { return this; }, visible() { return this; },
        ajax: {reload() { return {columns: {adjust() {}}}; }}};
    const location = {};
    Object.defineProperty(location, 'href', {set: value => navigations.push(value)});
    const context = {parent: {frames: {main: {location}}}, $: () => ui, formatDuration: () => "0:00"};
    const page = vm.runInNewContext('({' + method + accept + '})', context);
    Object.assign(page, {player: {tech, id: 17}, musicTable: ui, playQueueRepeatStatusCallback() {},
        playQueuePlayStatusCallback() {}, jukeBoxGainCallback() {}, getCurrentSongIndex() { return -1; }, updateCurrentImage() {}});
    return {page, navigations};
}
function queue(revision, entries = []) { return {revision, entries, repeatStatus: 'OFF', playStatus: 'STOPPED', gain: 0}; }

test('reconnect refresh does not download a second playlist for either external player', () => {
    for (const tech of ['EXTERNAL', 'EXTERNAL_WITH_PLAYLIST']) {
        const {page, navigations} = queuePage(tech);
        page.playQueueCallback(queue(1), true, false);
        page.playQueueCallback(queue(2), true, true);
        assert.deepEqual(navigations, ["<c:url value='/rest/exportLivePlayQueue'/>?playerId=17&c=airsonic-web"]);
        page.playQueueCallback(queue(3), false, false);
        assert.equal(navigations.length, tech === 'EXTERNAL_WITH_PLAYLIST' ? 2 : 1);
    }
});

test('reconnect refresh downloads a playlist when its contents changed while offline', () => {
    const {page, navigations} = queuePage('EXTERNAL_WITH_PLAYLIST');
    page.playQueueCallback(queue(1, [{id: 1}]), true, false);
    page.playQueueCallback(queue(2, [{id: 2}]), true, true);
    assert.equal(navigations.length, 2);
});

test('web player never downloads a playlist and stale updates remain ignored', () => {
    const {page, navigations} = queuePage('WEB');
    assert.equal(page.playQueueCallback(queue(2), true, false), true);
    assert.equal(page.playQueueCallback(queue(1), false, false), false);
    assert.equal(navigations.length, 0);
});

test('polling and commands download an external playlist only when tracks change', () => {
    const {page, navigations} = queuePage('EXTERNAL_WITH_PLAYLIST');
    page.acceptQueue({...queue(1), entry: [{id: 1}]}, true, false, false);
    page.acceptQueue({...queue(2), entry: [{id: 1}], repeatStatus: 'QUEUE'}, false, true, true);
    page.acceptQueue({...queue(3), entry: [{id: 1}]}, false, true, false);
    assert.equal(navigations.length, 1);
    page.acceptQueue({...queue(4), entry: [{id: 2}]}, false, true, true);
    assert.equal(navigations.length, 2);
});

test('REST and WebSocket queue entries retain values and supply missing table columns', () => {
    const fields = ['trackNumber', 'discNumber', 'title', 'album', 'artist', 'albumArtist', 'genre',
        'year', 'format', 'fileSize', 'duration', 'bitRate', 'playCount', 'lastPlayed',
        'lastScanned', 'created', 'changed', 'starred'];
    const {page} = queuePage('WEB');
    for (const [revision, entry] of [[1, {id: 1, title: 'Song', discNumber: 2, trackNumber: 0}],
        [2, {id: 2, title: 'No disc number', discNumber: null}]]) {
        page.playQueueCallback(queue(revision, [entry]), revision === 1, false);
        for (const field of fields) assert.ok(Object.hasOwn(page.songs[0], field), field);
        assert.equal(page.songs[0].discNumber, entry.discNumber);
        assert.equal(page.songs[0].title, entry.title);
        assert.equal(page.songs[0].albumArtist, null);
    }
    assert.equal(page.songs[0].trackNumber, null);
});

test('DataTables accepts the queue row without optional metadata', () => {
    const library = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/script/DataTables/datatables.js'), 'utf8');
    const start = library.indexOf('function _fnGetCellData(');
    const end = library.indexOf('\n\t}', start) + 3;
    assert.ok(start >= 0 && end > start);
    const warnings = [];
    const getCell = vm.runInNewContext('(' + library.slice(start, end) + ')', {
        _fnLog(settings, level, message) { warnings.push(message); }
    });
    const {page} = queuePage('WEB');
    page.playQueueCallback(queue(1, [{id: 1, title: 'Track'}]), true, false);
    const fields = ['trackNumber', 'discNumber', 'title', 'album', 'artist', 'albumArtist', 'genre',
        'year', 'format', 'fileSize', 'duration', 'bitRate', 'playCount', 'lastPlayed',
        'lastScanned', 'created', 'changed', 'starred'];
    const settings = {iDraw: 1, aoData: [{_aData: page.songs[0]}], aoColumns: fields.map(field => ({
        mData: field, sDefaultContent: null, fnGetData: row => row[field]
    }))};
    fields.forEach((field, i) => getCell(settings, 0, i, 'display'));
    assert.deepEqual(warnings, []);
    assert.equal(getCell(settings, 0, 1, 'display'), '');
});
