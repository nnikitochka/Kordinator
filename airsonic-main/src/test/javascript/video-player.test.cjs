const {test} = require('node:test');
const assert = require('node:assert/strict');
const {loadVideoPlayer, saveVideoBookmark} = require('../../main/webapp/script/video-player.js');
function setup() {
    const node = tag => ({tag, children: [], hidden: true, dataset: {}, setAttribute(name, value) {this[name] = value;},
        appendChild(child) {this.children.push(child);}});
    const nodes = Object.fromEntries(['videoPlayerData', 'videoPlayer', 'videoTitle', 'videoBack', 'share', 'download', 'starImage', 'videoError'].map(id => [id, node('div')]));
    nodes.videoPlayerData.dataset = {url: '/airsonic/rest/videoPlayer', backUrl: '/airsonic/main.view', shareUrl: '/airsonic/createShare.view',
        downloadUrl: '/airsonic/rest/downloadMedia', starOn: '/on.svg', starOff: '/off.svg', hlsUrl: '/hls.js', dashUrl: '/dash.js'};
    for (const item of Object.values(nodes)) Object.defineProperty(item, 'innerHTML', {set() {assert.fail('Unsafe HTML');}});
    const view = {location: {href: 'https://host/airsonic/videoPlayer.view?id=42&position=750&player=7&u=ignored'}};
    return {nodes, view, doc: {defaultView: view, baseURI: view.location.href, getElementById: id => nodes[id], createElement: node}};
}
function data() {
    return {videoId: 42, videoTitle: '<script>Видео & "тест"</script>', streamable: true, castable: true, starred: false,
        streamUrls: {Original: '/airsonic/stream?id=42&player=7&format=raw', '1500 Kbps': '/airsonic/stream?id=42&maxBitRate=1500'},
        defaultBitRate: 'Original', contentType: 'video/mp4', position: 750, autoBookmark: true, videoBookmarkFrequency: 5,
        canShare: true, canDownload: true, captions: [{identifier: 'Русский <script>', language: 'ru', url: '/airsonic/rest/captions?id=42&captionId=1'}],
        remoteStreamUrl: '/airsonic/ext/stream?id=42&jwt=stream', remoteCoverArtUrl: '/airsonic/ext/coverArt?id=42&jwt=cover',
        remoteCaptionsListUrl: '/airsonic/ext/rest/captions/list?id=42&jwt=captions'};
}
const response = body => ({ok: true, json: async () => body});
async function load(body = data(), extras = {}) {
    const state = setup(); state.calls = [];
    state.player = await loadVideoPlayer(state.doc, {fetch: async (url, options) => {
        state.calls.push({url, options}); return response(state.calls.length === 1 ? body : [{...body.captions[0], url: '/airsonic/ext/rest/captions?id=42&jwt=track'}]);
    }, createPlayer: (id, options) => {state.options = options; return {id};}, ...extras});
    await new Promise(resolve => setImmediate(resolve));
    return state;
}
test('loads REST data using only playback parameters and renders title, sources and tracks safely', async () => {
    const {nodes, calls, options, view} = await load();
    const endpoint = new URL(calls[0].url);
    assert.equal(endpoint.pathname, '/airsonic/rest/videoPlayer');
    assert.deepEqual([...endpoint.searchParams], [['id', '42'], ['position', '750'], ['player', '7']]);
    assert.equal(calls[1].options.credentials, 'omit');
    assert.equal(calls[0].options.cache, 'no-store'); assert.equal(calls[0].options.credentials, 'same-origin');
    assert.equal(nodes.videoTitle.textContent, data().videoTitle);
    assert.equal(nodes.videoPlayer.children.length, 3);
    assert.equal(nodes.videoPlayer.children[0]['data-quality'], 'Original');
    assert.equal(nodes.videoPlayer.children[0].type, 'video/mp4');
    assert.equal(nodes.videoPlayer.children[2].label, 'Русский <script> (ru)');
    assert.equal(options.defaultQuality, 'Original'); assert.ok(options.features.includes('chromecast'));
    assert.equal(view.videoModel.remoteCaptions.length, 1);
    assert.match(view.videoModel.remoteCaptions[0].url, /jwt=track/);
    options.qualityChangeCallback(null, null, '1500 Kbps'); assert.equal(view.videoModel.currentUrl, '1500 Kbps');
});
test('restores bookmark after metadata, preserves seek on quality changes and saves through actual media events', async () => {
    const state = await load(), events = {}, saved = [], positions = [];
    const media = {currentTime: 10, readyState: 0, addEventListener: (event, callback) => {events[event] = callback;}};
    const instance = {media: {}, getElement: x => x, buildspeed() {}, setSrc: src => {assert.match(src, /format=raw/);}, setCurrentTime: time => positions.push(time)};
    state.view.bookmarkApi = {create: (...args) => saved.push(args)};
    state.options.success(media, null, instance);
    assert.deepEqual(positions, []); events.timeupdate(); assert.deepEqual(saved, []); events.loadedmetadata(); assert.deepEqual(positions, [0.75]);
    events.loadedmetadata(); assert.deepEqual(positions, [0.75]);
    events.timeupdate(); events.pause(); events.seeked(); events.ended();
    assert.deepEqual(saved, [[42, 10000, 'Played on Web Video Player']]);
    assert.equal(state.view.videoPlayer, instance);
});
test('permissions control actions and stars change only after successful REST mutation', async () => {
    let mutation;
    const state = await load(data(), {setStar: (...args) => {mutation = args;}});
    assert.equal(state.nodes.share.hidden, false); assert.equal(state.nodes.download.hidden, false);
    state.nodes.share.onclick(); assert.equal(state.view.location.href, 'https://host/airsonic/createShare.view?id=42');
    state.nodes.download.onclick(); assert.equal(state.view.location.href, 'https://host/airsonic/rest/downloadMedia?id=42');
    assert.equal(state.nodes.starImage.src, '/off.svg'); state.nodes.starImage.onclick();
    assert.deepEqual(mutation.slice(0, 2), [[42], true]); assert.equal(state.nodes.starImage.src, '/off.svg');
    mutation[2](); assert.equal(state.nodes.starImage.src, '/on.svg');
    state.nodes.starImage.onclick(); assert.deepEqual(mutation.slice(0, 2), [[42], false]);
    mutation[2](); assert.equal(state.nodes.starImage.src, '/off.svg');
    const restricted = await load({...data(), canShare: false, canDownload: false});
    assert.equal(restricted.nodes.share.hidden, true); assert.equal(restricted.nodes.share.onclick, undefined);
    assert.equal(restricted.nodes.download.hidden, true); assert.equal(restricted.nodes.download.onclick, undefined);
});
test('HLS initializes without forcing HTML5 and optional casting caption errors do not break playback', async () => {
    let calls = 0;
    const state = await load({...data(), streamable: false, defaultBitRate: '1500 Kbps', contentType: 'application/x-mpegurl',
        streamUrls: {'1500 Kbps': '/airsonic/hls/hls.m3u8?id=42&maxBitRate=1500'}},
    {fetch: async () => ++calls === 1 ? response({...data(), streamable: false, defaultBitRate: '1500 Kbps',
        streamUrls: {'1500 Kbps': '/airsonic/hls/hls.m3u8?id=42&maxBitRate=1500'}}) : Promise.reject(new Error('offline'))});
    let src;
    state.options.success({addEventListener() {}}, null, {setSrc: value => {src = value;}, buildspeed: () => assert.fail('HLS speed override')});
    assert.match(src, /hls.m3u8/); assert.deepEqual(state.view.videoModel.remoteCaptions, []);
});
test('HTTP, redirect, network, schema and unsafe URL errors do not initialize the player', async () => {
    for (const result of [{ok: false}, {ok: true, redirected: true}, response({}),
        response({...data(), streamUrls: {Original: 'javascript:alert(1)'}}), response({...data(), remoteStreamUrl: 'javascript:alert(1)'})]) {
        const state = setup();
        await loadVideoPlayer(state.doc, {fetch: async () => result, createPlayer: () => assert.fail('Unexpected player')});
        assert.equal(state.nodes.videoError.textContent, 'Video unavailable.');
    }
    const state = setup();
    await loadVideoPlayer(state.doc, {fetch: async () => {throw new Error('offline');}, createPlayer: () => assert.fail('Unexpected player')});
    assert.equal(state.nodes.videoError.textContent, 'Video unavailable.');
});
test('bookmark saves ignore disabled autosave, invalid time and intermediate intervals', () => {
    const api = {create: () => assert.fail('Unexpected bookmark')};
    saveVideoBookmark({currentTime: 10}, {...data(), autoBookmark: false}, api);
    saveVideoBookmark({currentTime: NaN}, data(), api);
    saveVideoBookmark({currentTime: 11}, data(), api);
});

test('mutation clients receive the current REST response CSRF token', async () => {
    const state = setup(), configured = {};
    state.view.createSubsonicStars = options => {configured.stars = options; return () => {};};
    state.view.createSubsonicBookmarks = options => {configured.bookmarks = options; return {create() {}};};
    let calls = 0;
    await loadVideoPlayer(state.doc, {fetch: async () => response(++calls === 1 ? {...data(), csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'current-token'} : []), createPlayer() {}});
    for (const options of Object.values(configured)) {
        assert.equal(options.csrfHeader, 'X-CSRF-TOKEN'); assert.equal(options.csrfToken, 'current-token');
    }
    assert.ok(state.view.bookmarkApi); assert.equal(typeof state.view.setMediaFilesStarred, 'function');
});
