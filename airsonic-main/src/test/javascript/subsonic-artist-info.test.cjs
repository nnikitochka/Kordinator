const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function setup(fetch) {
    const context = {fetch, URLSearchParams};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/subsonic-artist-info.js'), 'utf8'), context);
    return context.createArtistInfo({url: '/rest/getArtistInfo', csrfHeader: 'X-CSRF', csrfToken: 'token'});
}
function response(id) { return {ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'ok', artistInfo: {biography: 'bio', similarArtist: [{id, name: 'artist'}]}, websiteArtistInfo: {topSong: [{id: 'song'}]}}})}; }
test('requests website details and maps the response', async () => {
    let call;
    const api = setup(async (url, options) => { call = {url, options}; return response('4'); });
    const result = await api.get(7);
    assert.equal(call.url, '/rest/getArtistInfo');
    assert.equal(call.options.headers['X-CSRF'], 'token');
    assert.equal(call.options.credentials, 'same-origin');
    assert.equal(call.options.body.get('id'), '7');
    assert.equal(call.options.body.get('includeWebsiteDetails'), 'true');
    assert.equal(result.similarArtists[0].mediaFileId, '4');
    assert.equal(result.topSongs[0].id, 'song');
});
test('ignores out-of-order responses', async () => {
    const pending = [];
    const api = setup(() => new Promise(resolve => pending.push(resolve)));
    const first = api.get(1), second = api.get(2);
    pending[1](response('2'));
    assert.equal((await second).similarArtists[0].mediaFileId, '2');
    pending[0](response('1'));
    assert.equal(await first, null);
});
test('rejects redirects and API errors', async () => {
    await assert.rejects(setup(async () => ({ok: true, redirected: true})).get(1));
    await assert.rejects(setup(async () => ({ok: true, redirected: false, json: async () => ({'subsonic-response': {status: 'failed'}})})).get(1));
});


test('top song rows include optional table fields and retain supplied data', async () => {
    const result = await setup(async () => ({ok: true, json: async () => ({'subsonic-response': {
        status: 'ok', artistInfo: {}, websiteArtistInfo: {topSong: [{id: 1, title: 'Song', duration: 0}]}
    }})})).get(1);
    const song = result.topSongs[0];
    for (const field of ['id', 'starred', 'title', 'album', 'artist', 'duration']) assert.ok(Object.hasOwn(song, field), field);
    assert.equal(song.album, null); assert.equal(song.duration, 0); assert.equal(song.title, 'Song');
    const page = fs.readFileSync(path.join(__dirname, '../../main/webapp/WEB-INF/jsp/mediaMain.jsp'), 'utf8');
    assert.match(page, /subsonic-artist-info\.js\?v=2/);
});
