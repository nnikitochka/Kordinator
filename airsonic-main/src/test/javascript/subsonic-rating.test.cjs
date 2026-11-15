// Run with: node airsonic-main/src/test/javascript/subsonic-rating.test.cjs
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');

const ok = () => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok'}})});
function setup(fetch) {
    let listener, successes = [], errors = 0;
    const buttons = [0, 1, 2, 3, 4, 5].map(rating => ({
        dataset: {mediaId: '42', mediaRating: String(rating)}, disabled: false
    }));
    const document = {
        addEventListener: (type, callback) => { assert.equal(type, 'click'); listener = callback; },
        querySelectorAll: selector => { assert.equal(selector, 'button[data-media-rating]'); return buttons; }
    };
    const context = {document, fetch, URLSearchParams};
    vm.runInNewContext(readFileSync('airsonic-main/src/main/webapp/script/subsonic-rating.js', 'utf8'), context);
    context.installSubsonicRating({
        url: '/airsonic/rest/setRating', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'session-csrf',
        onSuccess: id => successes.push(id), onError: () => errors++
    });
    return {
        buttons, successes, errors: () => errors,
        click: rating => listener({target: {closest: () => buttons[rating]}, preventDefault() {}}),
        unrelated: () => listener({target: {closest: () => null}})
    };
}

test('delegated clicks submit ratings 1–5 and clear via zero using the session and CSRF', async () => {
    const requests = [];
    const fixture = setup(async (url, options) => { requests.push({url, options}); return ok(); });
    for (const rating of [1, 2, 3, 4, 5, 0]) {
        await fixture.click(rating);
        const {url, options} = requests.at(-1);
        assert.equal(url, '/airsonic/rest/setRating');
        assert.equal(options.method, 'POST');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.headers['X-CSRF-TOKEN'], 'session-csrf');
        assert.equal(options.body.get('id'), '42');
        assert.equal(options.body.get('rating'), String(rating));
        assert.equal(options.body.get('f'), 'json');
        assert.equal(options.body.get('c'), 'airsonic-web');
        assert.equal(options.body.has('p'), false);
        assert.equal(fixture.buttons.some(button => button.disabled), false);
    }
    assert.deepEqual(fixture.successes, Array(6).fill('42'));
    assert.equal(fixture.errors(), 0);
});

test('waits for success before refreshing; suppresses duplicate clicks and restores controls', async () => {
    let release, requests = 0;
    const response = new Promise(resolve => { release = resolve; });
    const fixture = setup(() => { requests++; return response; });
    const pending = fixture.click(3);
    assert.equal(fixture.buttons.every(button => button.disabled), true);
    assert.equal(fixture.successes.length, 0);
    await fixture.click(4);
    assert.equal(requests, 1);
    release(ok());
    await pending;
    assert.deepEqual(fixture.successes, ['42']);
    assert.equal(fixture.buttons.some(button => button.disabled), false);
});

for (const [name, failedResponse] of Object.entries({
    http: async () => ({ok: false}),
    redirect: async () => ({ok: true, redirected: true}),
    network: async () => { throw new Error('offline'); },
    json: async () => ({ok: true, json: async () => { throw new Error('HTML login page'); }}),
    protocol: async () => ({ok: true, json: async () => ({'subsonic-response': {status: 'failed'}})}),
    envelope: async () => ({ok: true, json: async () => ({})})
})) {
    test(`${name} failure leaves the rating unchanged and allows retry`, async () => {
        let fail = true;
        const fixture = setup(() => fail ? failedResponse() : Promise.resolve(ok()));
        await fixture.click(5);
        assert.equal(fixture.errors(), 1);
        assert.deepEqual(fixture.successes, []);
        assert.equal(fixture.buttons.some(button => button.disabled), false);
        fail = false;
        await fixture.click(5);
        assert.deepEqual(fixture.successes, ['42']);
    });
}

test('ignores unrelated clicks and disabled controls', async () => {
    const fixture = setup(() => assert.fail('unexpected request'));
    await fixture.unrelated();
    fixture.buttons[2].disabled = true;
    await fixture.click(2);
});

test('page refresh targets the rated directory only if it is still displayed', () => {
    const source = readFileSync('airsonic-main/src/main/webapp/WEB-INF/jsp/mediaMain.jsp', 'utf8');
    const callback = source.match(/onSuccess: (function \(id\) \{[\s\S]*?\n        \}),/)[1];
    const calls = [];
    const context = {mediaDir: {id: 42}, getMediaDirectory: id => calls.push(id)};
    const refresh = vm.runInNewContext('(' + callback + ')', context);
    refresh('42');
    context.mediaDir.id = 99;
    refresh('42');
    assert.deepEqual(calls, ['42']);
});

class RatingElement {
    constructor(tagName) {
        this.tagName = tagName;
        this.children = [];
        this.dataset = {};
        this.style = {};
    }
    appendChild(child) { this.children.push(child); return child; }
    replaceChildren(...children) { this.children = children; }
}

function renderFixture() {
    const doc = {
        createElement: tagName => new RatingElement(tagName),
        createTextNode: text => Object.assign(new RatingElement('#text'), {textContent: text})
    };
    const container = new RatingElement('div');
    container.ownerDocument = doc;
    const context = {document: {}, URLSearchParams};
    vm.runInNewContext(readFileSync('airsonic-main/src/main/webapp/script/subsonic-rating.js', 'utf8'), context);
    return {container, render: context.renderSubsonicRating};
}

const ratingImages = {
    ratingOnImage: '/on.svg', ratingHalfImage: '/half.svg', ratingOffImage: '/off.svg',
    clearRatingImage: '/clear.svg', ratingLabel: 'Rating', clearRatingLabel: 'Clear rating'
};

test('readonly rating renders five full, half, and empty stars on the 0–50 display scale', () => {
    const {container, render} = renderFixture();
    for (const [score, expected] of [
        [0, ['/off.svg', '/off.svg', '/off.svg', '/off.svg', '/off.svg']],
        [30, ['/on.svg', '/on.svg', '/on.svg', '/off.svg', '/off.svg']],
        [35, ['/on.svg', '/on.svg', '/on.svg', '/half.svg', '/off.svg']],
        [50, ['/on.svg', '/on.svg', '/on.svg', '/on.svg', '/on.svg']]
    ]) {
        render(container, score, null, true, ratingImages);
        assert.deepEqual(container.children.map(image => image.src), expected);
        assert.equal(container.children.length, 5);
        assert.equal(container.children[0].title, 'Rating ' + score / 10);
        assert.equal(container.children.every(image => image.tagName === 'img'), true);
    }
});

test('editable rating uses the media id and 1–5 commands plus a clear command of zero', () => {
    const {container, render} = renderFixture();
    render(container, 30, 812, false, ratingImages);
    const buttons = container.children.filter(child => child.tagName === 'button');
    assert.deepEqual(buttons.map(button => button.dataset.mediaRating), ['1', '2', '3', '4', '5', '0']);
    assert.equal(buttons.every(button => button.dataset.mediaId === '812'), true);
    assert.deepEqual(buttons.slice(0, 5).map(button => button.children[0].src),
        ['/on.svg', '/on.svg', '/on.svg', '/off.svg', '/off.svg']);
    assert.equal(buttons[5].children[0].src, '/clear.svg');
    assert.equal(buttons[5].children[0].title, 'Clear rating');
});
