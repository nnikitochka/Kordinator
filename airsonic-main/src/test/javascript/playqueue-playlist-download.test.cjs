const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/WEB-INF/jsp/playQueue.jsp'), 'utf8');
const method = jsp.slice(jsp.indexOf('        playQueueCallback(playQueue, initial, suppressPlaylistDownload) {'), jsp.indexOf('        updateWindowTitle(song) {'));
function queuePage(tech) {
    const navigations = [];
    const ui = {text() { return this; }, html() { return this; }, column() { return this; }, visible() { return this; },
        ajax: {reload() { return {columns: {adjust() {}}}; }}};
    const location = {};
    Object.defineProperty(location, 'href', {set: value => navigations.push(value)});
    const context = {parent: {frames: {main: {location}}}, $: () => ui, formatDuration: () => "0:00"};
    const page = vm.runInNewContext('({' + method + '})', context);
    Object.assign(page, {player: {tech}, musicTable: ui, playQueueRepeatStatusCallback() {},
        playQueuePlayStatusCallback() {}, jukeBoxGainCallback() {}, getCurrentSongIndex() { return -1; }, updateCurrentImage() {}});
    return {page, navigations};
}
function queue(revision, entries = []) { return {revision, entries, repeatStatus: 'OFF', playStatus: 'STOPPED', gain: 0}; }

test('reconnect refresh does not download a second playlist for either external player', () => {
    for (const tech of ['EXTERNAL', 'EXTERNAL_WITH_PLAYLIST']) {
        const {page, navigations} = queuePage(tech);
        page.playQueueCallback(queue(1), true, false);
        page.playQueueCallback(queue(2), true, true);
        assert.deepEqual(navigations, ['play.m3u?']);
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

test('connection refresh passes the suppression flag through the REST response', () => {
    assert.match(jsp, /pq\.requestQueue\("get", \{\}, true, true\)/);
    assert.match(jsp, /pq\.playQueueCallback\(queue, initial, suppressPlaylistDownload\)/);
});
