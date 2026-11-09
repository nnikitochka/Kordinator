const test = require('node:test');
const assert = require('node:assert/strict');
const {loadPasswordRecovery} = require('../../main/webapp/script/password-recovery.js');
const config = {csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'session-token'};
const response = body => ({ok: true, redirected: false, json: async () => body});
function setup() {
    const errors = ['usernotfound', 'noemail', 'sendfailed', 'invalidcaptcha'].map(key => ({hidden: true, dataset: {recoveryError: 'recover.error.' + key}}));
    const nodes = {
        recoveryForm: {action: 'https://music.test/airsonic/rest/recover', addEventListener: (event, fn) => {nodes.recoveryForm[event] = fn;}},
        recoverySubmit: {disabled: true}, usernameOrEmail: {value: ' member@example.test '},
        recoveryInputs: {hidden: false}, recoveryCaptcha: {dataset: {}},
        recoverySuccess: {hidden: true, dataset: {template: 'Sent to {0}.'}}
    };
    const scripts = [], calls = [];
    const doc = {defaultView: {}, getElementById: id => nodes[id], querySelectorAll: () => errors,
        createElement: () => ({}), head: {appendChild: script => scripts.push(script)}};
    return {doc, nodes, errors, scripts, calls};
}
async function load(results, captcha) {
    const state = setup();
    state.doc.defaultView.grecaptcha = captcha;
    await loadPasswordRecovery(state.doc, {fetch: async (url, options) => {
        state.calls.push({url, options});
        const next = results.shift();
        if (next instanceof Error) throw next;
        return typeof next === 'function' ? next() : next;
    }});
    state.submit = () => state.nodes.recoveryForm.submit({preventDefault() {state.prevented = true;}});
    return state;
}
test('loads anonymous configuration and submits with session CSRF, preserving context path', async () => {
    const state = await load([response(config), response({sentTo: '<img src=x>@example.test'})]);
    assert.equal(state.nodes.recoverySubmit.disabled, false);
    assert.equal(state.calls[0].url, 'https://music.test/airsonic/rest/recover');
    assert.equal(state.calls[0].options.cache, 'no-store');
    assert.equal(state.calls[0].options.credentials, 'same-origin');
    await state.submit();
    assert.equal(state.prevented, true);
    assert.equal(state.calls[1].options.method, 'POST');
    assert.equal(state.calls[1].options.headers['X-CSRF-TOKEN'], 'session-token');
    assert.equal(state.calls[1].options.credentials, 'same-origin');
    assert.equal(new URLSearchParams(state.calls[1].options.body).get('usernameOrEmail'), ' member@example.test ');
    assert.equal(state.nodes.recoverySuccess.textContent, 'Sent to <img src=x>@example.test.');
    assert.equal(state.nodes.recoveryInputs.hidden, true);
    assert.equal(state.nodes.recoverySuccess.hidden, false);
    assert.equal(state.scripts.length, 0);
});
test('renders captcha before loading Google script and sends its response', async () => {
    let resets = 0;
    const state = await load([response({...config, recaptchaSiteKey: 'public-key'}), response({error: 'recover.error.invalidcaptcha'})],
        {getResponse: () => 'captcha-answer', reset: () => resets++});
    assert.equal(state.nodes.recoveryCaptcha.dataset.sitekey, 'public-key');
    assert.equal(state.scripts[0].src, 'https://www.google.com/recaptcha/api.js');
    await state.submit();
    assert.equal(new URLSearchParams(state.calls[1].options.body).get('g-recaptcha-response'), 'captcha-answer');
    assert.equal(state.errors.find(node => !node.hidden).dataset.recoveryError, 'recover.error.invalidcaptcha');
    assert.equal(resets, 1);
    assert.equal(state.nodes.recoveryInputs.hidden, false);
});
test('all known server errors use localized messages; retry clears the previous error', async () => {
    const keys = ['usernotfound', 'noemail', 'sendfailed', 'invalidcaptcha'];
    const state = await load([response(config), ...keys.map(key => response({error: 'recover.error.' + key})), response({sentTo: 'member@example.test'})]);
    for (const key of keys) {
        await state.submit();
        assert.deepEqual(state.errors.filter(node => !node.hidden).map(node => node.dataset.recoveryError), ['recover.error.' + key]);
        assert.equal(state.nodes.recoverySubmit.disabled, false);
    }
    await state.submit();
    assert.equal(state.errors.every(node => node.hidden), true);
});
test('missing CSRF, redirects and unavailable configuration keep submission disabled', async () => {
    for (const result of [response({}), {ok: false}, {ok: true, redirected: true}, new Error('network'), response(null)]) {
        const state = await load([result]);
        assert.equal(state.nodes.recoverySubmit.disabled, true);
        await state.submit();
        assert.equal(state.calls.length, 1);
        assert.equal(state.errors.find(node => !node.hidden).dataset.recoveryError, 'recover.error.sendfailed');
    }
});
test('submission failures allow retries and never render untrusted error text', async () => {
    const state = await load([response(config), new Error('network'), {ok: false}, {ok: true, redirected: true}, response({error: '<script>'})]);
    for (let i = 0; i < 4; i++) {
        await state.submit();
        assert.equal(state.nodes.recoverySubmit.disabled, false);
        assert.equal(state.errors.find(node => !node.hidden).dataset.recoveryError, 'recover.error.sendfailed');
    }
});
test('ignores duplicate submissions while SMTP request is pending', async () => {
    let finish;
    const pending = new Promise(resolve => {finish = resolve;});
    const state = await load([response(config), () => pending]);
    const first = state.submit();
    await state.submit();
    assert.equal(state.calls.length, 2);
    assert.equal(state.nodes.recoverySubmit.disabled, true);
    finish(response({sentTo: 'member@example.test'}));
    await first;
    assert.equal(state.nodes.recoverySuccess.hidden, false);
});
