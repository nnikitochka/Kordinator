const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const test = require('node:test');

const code = fs.readFileSync('airsonic-main/src/main/webapp/script/subsonic-database-status.js', 'utf8');

test('polls authenticated REST status, retries invalid data and stops on pagehide', async () => {
    const timers = [];
    let hide, requests = 0;
    const updates = [];
    const operation = {version: 1, running: false, failed: false, message: 'complete'};
    const window = {
        setTimeout(fn, delay) { assert.equal(delay, 2000); timers.push(fn); return timers.length; },
        clearTimeout() {},
        addEventListener(name, fn) { assert.equal(name, 'pagehide'); hide = fn; }
    };
    const context = {window, URLSearchParams, console: {error() {}}, fetch: async (url, options) => {
        assert.equal(url, '/rest/getDatabaseStatus');
        assert.equal(options.method, 'POST');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.headers['X-CSRF-TOKEN'], 'token');
        requests++;
        return {ok: true, json: async () => requests === 1 ? {} : {
            'subsonic-response': {status: 'ok', databaseStatuses: {backup: operation, export: operation, import: operation}}
        }};
    }};
    vm.runInNewContext(code, context);
    context.watchDatabaseStatus({url: '/rest/getDatabaseStatus', csrfHeader: 'X-CSRF-TOKEN', csrfToken: 'token'}, value => updates.push(value));
    await new Promise(setImmediate);
    assert.equal(updates.length, 0);
    timers.shift()();
    await new Promise(setImmediate);
    assert.equal(updates.length, 1);
    assert.equal(updates[0].backup.message, 'complete');
    hide();
    timers.shift()();
    await new Promise(setImmediate);
    assert.equal(requests, 2);
});
