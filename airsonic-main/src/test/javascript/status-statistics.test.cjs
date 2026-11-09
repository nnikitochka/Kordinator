const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('airsonic-main/src/main/webapp/script/status-statistics.js', 'utf8');
const response = body => ({ok: true, json: async () => ({'subsonic-response': {status: 'ok', ...body}})});

test('reads Subsonic envelopes using authenticated requests without caching', async () => {
    const calls = [];
    const context = {fetch: async (url, options) => {
        calls.push({url, options});
        return url.includes('getUser') ? response({userTransferStatistics: {user: [{username: 'listener', streamed: 1099511627776}]}}) :
            response({transferStatistics: {transfer: [{transferType: 'stream', sample: [{timestamp: 1000, bytesTransferred: 4096}]}]}});
    }};
    vm.runInNewContext(code, context);
    const api = context.createStatusStatistics({baseUrl: '/airsonic/rest/'});
    assert.equal((await api.transfers())[0].sample[0].bytesTransferred, 4096);
    assert.equal((await api.users())[0].streamed, 1099511627776);
    assert.equal(calls[0].url, '/airsonic/rest/getTransferStatistics?f=json&c=airsonic-web');
    assert.equal(calls[1].url, '/airsonic/rest/getUserTransferStatistics?f=json&c=airsonic-web');
    for (const call of calls) {
        assert.equal(call.options.credentials, 'same-origin');
        assert.equal(call.options.cache, 'no-store');
    }
});

test('accepts empty snapshots and rejects redirects, errors and malformed statistics', async () => {
    let next;
    const context = {fetch: async () => next};
    vm.runInNewContext(code, context);
    const api = context.createStatusStatistics({baseUrl: '/rest/'});
    next = response({transferStatistics: {}});
    assert.equal((await api.transfers()).length, 0);
    for (const invalid of [{ok: false}, {...response({}), redirected: true}, response({}),
        response({transferStatistics: {transfer: {}}}), response({transferStatistics: {transfer: false}}),
        {ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {message: 'denied'}}})}]) {
        next = invalid;
        await assert.rejects(api.transfers());
    }
    next = response({transferStatistics: {transfer: []}});
    assert.equal((await api.transfers()).length, 0);
});

function element(tag) {
    return {tag, children: [], textContent: '',
        appendChild(child) { this.children.push(child); },
        replaceChildren() { this.children = []; },
        set innerHTML(value) { assert.fail('transfer values must be rendered as text'); }
    };
}
test('renders untrusted names and paths as text, preserves chart slots and clears stale rows', () => {
    const body = element('tbody');
    const document = {querySelector: () => body, createElement: element, createTextNode: value => ({textContent: value})};
    const context = {};
    vm.runInNewContext(code, context);
    const value = '<img src=x onerror=alert(1)>';
    context.renderTransferStatistics(document, [{transferType: 'stream', playerDescription: value, playerType: value,
        username: value, path: value, bytesTransferred: '8 KB'}, {transferType: 'upload'}], {stream: 'Stream', upload: 'Upload', null: 'Unknown'});
    assert.equal(body.children.length, 2);
    const cells = body.children[0].children;
    assert.equal(cells[0].textContent, 'Stream');
    assert.equal(cells[1].textContent, value);
    assert.equal(cells[1].children[1].textContent, value);
    assert.equal(cells[2].textContent, value);
    assert.equal(cells[3].textContent, value);
    assert.equal(cells[5].children[0].id, 'transfersChart0');
    assert.equal(body.children[1].children[1].textContent, 'Unknown');
    context.renderTransferStatistics(document, [], {});
    assert.equal(body.children.length, 0);
});
