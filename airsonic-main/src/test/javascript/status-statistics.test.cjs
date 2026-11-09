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

test('loads scheduled tasks through authenticated REST', async () => {
    const context = {fetch: async (url, options) => {
        assert.equal(url, '/airsonic/rest/getScheduledTasks?f=json&c=airsonic-web');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.cache, 'no-store');
        return response({scheduledTasks: {task: [{name: 'scan', type: 'FIXED_DELAY'}]}});
    }};
    vm.runInNewContext(code, context);
    assert.equal((await context.createStatusStatistics({baseUrl: '/airsonic/rest/'}).scheduledTasks())[0].name, 'scan');
});

test('renders safe task names, absent and incalculable dates, and unknown schedules', () => {
    const body = element('tbody');
    const document = {querySelector: () => body, createElement: element};
    const context = {};
    vm.runInNewContext(code, context);
    context.renderScheduledTasks(document, [{name: '<img src=x>', type: 'FIXED_DELAY', scheduledBy: '<source>',
        created: '2026-01-01T00:00:00Z', firstRun: 'not a date', lastRun: null, nextRun: '-1000000000-01-01T00:00:00Z'},
        {name: 'unknown', type: null}], {FIXED_DELAY: 'Delay', never: 'Never', incalculable: 'Unknown time', null: 'Unknown'});
    const cells = body.children[0].children;
    assert.equal(cells[0].textContent, '<img src=x>');
    assert.equal(cells[1].textContent, 'Delay');
    assert.notEqual(cells[2].textContent, 'Invalid Date');
    assert.equal(cells[3].textContent, 'not a date');
    assert.equal(cells[4].textContent, 'Never');
    assert.equal(cells[5].textContent, 'Unknown time');
    assert.equal(cells[6].textContent, '<source>');
    assert.equal(body.children[1].children[1].textContent, 'Unknown');
    context.renderScheduledTasks(document, [], {});
    assert.equal(body.children.length, 0);
});

test('reads watcher snapshots, renders names and paths safely and clears removed watchers', async () => {
    let next = response({pathWatchers: {watcher: [{name: '<watcher>', path: '/music/<img src=x>'}]}});
    const context = {fetch: async (url, options) => {
        assert.equal(url, '/airsonic/rest/getPathWatchers?f=json&c=airsonic-web');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.cache, 'no-store');
        return next;
    }};
    vm.runInNewContext(code, context);
    const api = context.createStatusStatistics({baseUrl: '/airsonic/rest/'});
    const body = element('tbody');
    const document = {querySelector: selector => {
        assert.equal(selector, '#pathWatcherTable > tbody'); return body;
    }, createElement: element};
    context.renderPathWatchers(document, await api.pathWatchers());
    assert.equal(body.children.length, 1);
    assert.equal(body.children[0].children[0].textContent, '<watcher>');
    assert.equal(body.children[0].children[1].textContent, '/music/<img src=x>');
    next = response({pathWatchers: {}});
    context.renderPathWatchers(document, await api.pathWatchers());
    assert.equal(body.children.length, 0);
    next = response({pathWatchers: {watcher: {}}});
    await assert.rejects(api.pathWatchers());
});

test('loads caches in one request and clears exactly one encoded cache with CSRF', async () => {
    const calls = [];
    let next = response({cacheStatistics: {cache: [{name: 'a', hits: 12, misses: 3}]}});
    const context = {fetch: async (url, options) => { calls.push({url, options}); return next; }};
    vm.runInNewContext(code, context);
    const api = context.createStatusStatistics({baseUrl: '/airsonic/rest/', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token'});
    assert.equal((await api.caches())[0].hits, 12);
    assert.equal(calls[0].url, '/airsonic/rest/getCacheStatistics?f=json&c=airsonic-web');
    next = response({});
    await api.clearCache('<cache /&>');
    const clear = calls[1];
    assert.equal(clear.url, '/airsonic/rest/clearCache?f=json&c=airsonic-web');
    assert.equal(clear.options.method, 'POST');
    assert.equal(clear.options.headers['X-CSRF-TOKEN'], 'token');
    assert.equal(new URLSearchParams(clear.options.body).get('cache'), '<cache /&>');
    assert.equal(clear.options.credentials, 'same-origin');
    assert.equal(clear.options.cache, 'no-store');
    next = {ok: true, json: async () => ({'subsonic-response': {status: 'failed', error: {message: 'unknown cache'}}})};
    await assert.rejects(api.clearCache('missing'), /unknown cache/);
    next = response({cacheStatistics: {}});
    assert.equal((await api.caches()).length, 0);
});

test('renders cache hit ratios, unavailable counters, and safe clearing controls', async () => {
    const body = element('tbody');
    const document = {querySelector: selector => { assert.equal(selector, '#cachesTable > tbody'); return body; },
        createElement: tag => { const node = element(tag); node.addEventListener = (name, fn) => { node[name] = fn; }; return node; }};
    const errors = [];
    const context = {console: {error: error => errors.push(error)}};
    vm.runInNewContext(code, context);
    let resolve;
    const names = [];
    context.renderCacheStatistics(document, [{name: '<cache onclick=x>', hits: 3, misses: 1, puts: 2, removals: 0},
        {name: 'empty', hits: 0, misses: 0}, {name: 'unavailable'}], {cacheevict: 'Clear'}, name => {
        names.push(name); return new Promise(r => { resolve = r; });
    });
    const cells = body.children[0].children;
    assert.equal(cells[0].textContent, '<cache onclick=x>');
    assert.equal(cells[1].textContent, 75);
    assert.equal(cells[5].textContent, 0);
    assert.equal(body.children[1].children[1].textContent, 'N/A');
    assert.equal(body.children[2].children[2].textContent, 'N/A');
    const button = cells[6].children[0];
    assert.equal(button.textContent, 'Clear');
    const pending = button.click();
    assert.equal(button.disabled, true);
    assert.deepEqual(names, ['<cache onclick=x>']);
    resolve(); await pending;
    assert.equal(button.disabled, false);
    context.renderCacheStatistics(document, [{name: 'failed'}], {cacheevict: 'Clear'}, async () => { throw Error('denied'); });
    const failed = body.children[0].children[6].children[0];
    await failed.click();
    assert.equal(failed.disabled, false);
    assert.equal(errors[0].message, 'denied');
    context.renderCacheStatistics(document, [], {}, () => {});
    assert.equal(body.children.length, 0);
});
