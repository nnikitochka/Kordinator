const {test} = require('node:test');
const assert = require('node:assert/strict');
const {loadPublicShare} = require('../../main/webapp/script/public-share.js');
function setup() {
    const element = tag => ({tag, children: [], style: {}, setAttribute(name, value) {this[name] = value;},
        appendChild(child) {this.children.push(child);}});
    const nodes = Object.fromEntries(['shareTitle', 'shareSubtitle', 'sharePlayer', 'publicShareData'].map(id => [id, element('div')]));
    nodes.publicShareData.dataset = {url: 'https://host/airsonic/ext/rest/share/Test?jwt=secret'};
    for (const node of Object.values(nodes)) Object.defineProperty(node, 'innerHTML', {set() {assert.fail('Unsafe HTML');}});
    return {nodes, doc: {baseURI: 'https://host/airsonic/ext/share/Test', getElementById: id => nodes[id],
        createElement: element, querySelector: () => null, head: element('head')}};
}
function data() {
    return {available: true, videoPresent: false, description: `Music <img src=x onerror=alert(1)> " '&`, username: 'owner',
        media: [{title: '<script>track</script>', artist: 'Artist & artist', album: 'Album', contentType: 'audio/mpeg',
            streamUrl: 'https://host/ext/stream?id=1&jwt=x', coverArtUrl: 'https://host/ext/coverArt?id=1&jwt=x', captionsUrl: 'https://host/ext/captions/list?id=1&jwt=x'}]};
}
test('loads signed REST data and safely initializes audio player with playlist and controls', async () => {
    const {doc, nodes} = setup(); let sent, options;
    await loadPublicShare(doc, {fetch: async (url, init) => {sent = {url, init}; return {ok: true, json: async () => data()};},
        createPlayer: (id, config) => {assert.equal(id, 'player'); options = config;}});
    assert.equal(sent.url, nodes.publicShareData.dataset.url); assert.equal(sent.init.cache, 'no-store');
    assert.equal(nodes.shareTitle.textContent, data().description); assert.equal(nodes.shareSubtitle.textContent, 'owner');
    assert.equal(nodes.sharePlayer.children[0].tag, 'audio'); assert.equal(nodes.sharePlayer.children[0].preload, 'none');
    assert.equal(options.playlist[0].title, '&lt;script&gt;track&lt;/script&gt;');
    assert.equal(options.playlist[0]['data-playlist-description'], 'Artist &amp; artist');
    assert.equal(options.playlist[0].src, data().media[0].streamUrl);
    assert.ok(options.features.includes('playlist')); assert.ok(options.features.includes('chromecast'));
    assert.ok(options.playlistTitle.includes('&lt;img')); assert.equal(doc.head.children.length, 2);
});
test('creates video player and falls back to album metadata without description', async () => {
    const {doc, nodes} = setup(); const body = data(); body.videoPresent = true; body.description = null;
    await loadPublicShare(doc, {fetch: async () => ({ok: true, json: async () => body}), createPlayer: () => {}});
    assert.equal(nodes.sharePlayer.children[0].tag, 'video');
    assert.equal(nodes.shareTitle.textContent, body.media[0].artist); assert.equal(nodes.shareSubtitle.textContent, 'Album');
});
test('unavailable, expired, malformed, redirected and unsafe responses never initialize playback', async () => {
    for (const body of [{available: false}, {available: true, media: []}, {available: true},
        {...data(), media: [{...data().media[0], streamUrl: 'javascript:alert(1)'}]}]) {
        const {doc, nodes} = setup();
        await loadPublicShare(doc, {fetch: async () => ({ok: true, json: async () => body}), createPlayer: () => assert.fail('unexpected player')});
        assert.equal(nodes.shareTitle.textContent, 'Sorry, the content is not available.'); assert.equal(nodes.sharePlayer.children.length, 0);
    }
    for (const response of [{ok: false}, {ok: true, redirected: true}, {ok: true, json: async () => {throw new Error('JSON');}}]) {
        const {doc, nodes} = setup();
        await loadPublicShare(doc, {fetch: async () => response, createPlayer: () => assert.fail('unexpected player')});
        assert.equal(nodes.shareTitle.textContent, 'Sorry, the content is not available.');
    }
    const {doc, nodes} = setup();
    await loadPublicShare(doc, {fetch: async () => {throw new Error('offline');}});
    assert.equal(nodes.sharePlayer.children.length, 0);
});
test('caption failure leaves playback and speed controls available', async () => {
    const {doc} = setup(); let config, listener, speed = 0, calls = 0;
    await loadPublicShare(doc, {fetch: async () => ++calls === 1 ? {ok: true, json: async () => data()} : {ok: false},
        createPlayer: (_, options) => {config = options;}});
    global.$ = () => ({on: (_, callback) => {listener = callback;}});
    try {
        const player = {getElement: x => x, buildspeed: () => {speed++;}, rebuildtracks: () => assert.fail('no captions')};
        config.success({originalNode: {setAttribute() {}}}, null, player);
        await listener({detail: {type: 'audio/mpeg', 'data-playlist-caption': data().media[0].captionsUrl}});
        assert.equal(speed, 1);
    } finally {delete global.$;}
});
