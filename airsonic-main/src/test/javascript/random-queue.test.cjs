const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('airsonic-main/src/main/webapp/script/random-queue.js', 'utf8');

function setup(fetch, player = 42) {
    const buttons = [{name: 'addToPlaylist', value: 'Добавить'}, {name: 'autoRandom', value: 'Радио'}];
    const error = {hidden: true, textContent: ''};
    Object.defineProperty(error, 'innerHTML', {set() { assert.fail('errors must be text'); }});
    let submit, saved = 0;
    const updated = [];
    const form = {addEventListener: (event, listener) => { assert.equal(event, 'submit'); submit = listener; },
        querySelector: () => buttons[0], querySelectorAll: () => buttons};
    const context = {fetch, URLSearchParams, FormData: function (received) {
        assert.equal(received, form);
        return [['size', '20'], ['genre', 'Rock & Roll'], ['year', '2000 2005'], ['albumRatingValue', ''], ['musicFolderId', '-1']];
    }};
    vm.runInNewContext(code, context);
    context.bindRandomQueue(form, error, {url: '/airsonic/rest/createRandomPlayQueue', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token',
        player: () => player, save: () => { saved++; }, updated: id => updated.push(id)});
    return {buttons, error, updated, saved: () => saved, submit: index => submit({preventDefault() {}, submitter: buttons[index]})};
}
const response = body => ({ok: true, json: async () => ({'subsonic-response': body})});

test('preserves filter values and the clicked append/radio action with CSRF and the selected player', async () => {
    for (const index of [0, 1]) {
        let request;
        const page = setup(async (url, options) => { request = {url, options}; return response({status: 'ok'}); });
        await page.submit(index);
        assert.equal(request.url, '/airsonic/rest/createRandomPlayQueue');
        const params = new URLSearchParams(request.options.body);
        assert.equal(params.get('genre'), 'Rock & Roll');
        assert.equal(params.get('year'), '2000 2005');
        assert.equal(params.get('albumRatingValue'), '');
        assert.equal(params.get('musicFolderId'), '-1');
        assert.equal(params.get('playerId'), '42');
        assert.equal(params.has('addToPlaylist'), index === 0);
        assert.equal(params.has('autoRandom'), index === 1);
        assert.equal(params.get('f'), 'json');
        assert.equal(params.get('c'), 'airsonic-web');
        assert.equal(request.options.method, 'POST');
        assert.equal(request.options.credentials, 'same-origin');
        assert.equal(request.options.cache, 'no-store');
        assert.equal(request.options.headers['X-CSRF-TOKEN'], 'token');
        assert.match(request.options.headers['Content-Type'], /application\/x-www-form-urlencoded/);
        assert.equal(page.saved(), 1);
        assert.deepEqual(page.updated, [42]);
        assert.equal(page.error.hidden, true);
        assert(page.buttons.every(button => !button.disabled));
    }
});

test('standalone pages use the existing website player and Enter selects the first action', async () => {
    let params;
    const page = setup(async (url, options) => { params = new URLSearchParams(options.body); return response({status: 'ok'}); }, null);
    await page.submit();
    assert.equal(params.has('playerId'), false);
    assert.equal(params.has('addToPlaylist'), true);
    assert.deepEqual(page.updated, [null]);
});

test('prevents duplicate submissions, displays safe errors and allows retry', async () => {
    let finish, requests = 0;
    const page = setup(() => { requests++; return new Promise(resolve => { finish = resolve; }); });
    const first = page.submit(1);
    assert(page.buttons.every(button => button.disabled));
    await page.submit(0);
    assert.equal(requests, 1);
    finish(response({status: 'failed', error: {message: '<img src=x onerror=alert(1)>'}}));
    await first;
    assert.equal(page.error.hidden, false);
    assert.equal(page.error.textContent, '<img src=x onerror=alert(1)>');
    assert.equal(page.updated.length, 0);
    assert(page.buttons.every(button => !button.disabled));
    const retry = page.submit(0);
    assert.equal(page.error.hidden, true);
    finish(response({status: 'ok'}));
    await retry;
    assert.equal(requests, 2);
    assert.equal(page.updated.length, 1);
});

test('HTTP errors, redirects, malformed responses and network errors never refresh the queue', async () => {
    for (const next of [{ok: false}, {...response({status: 'ok'}), redirected: true},
        {ok: true, json: async () => ({})}, response({}), new Error('Offline')]) {
        const page = setup(async () => { if (next instanceof Error) throw next; return next; });
        await page.submit(0);
        assert.equal(page.updated.length, 0);
        assert.equal(page.error.hidden, false);
        assert(page.buttons.every(button => !button.disabled));
    }
});
