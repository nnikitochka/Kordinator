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

test('database page displays REST operation status and enables actions when operations finish', async () => {
    const elements = {};
    for (const id of ['databaseSettings', 'databaseFields', 'databaseActions', 'databaseError', 'databaseSaved',
        'databaseOperation', 'databaseImport', 'backupdb', 'exportdb', 'importdb']) {
        elements[id] = {addEventListener() {}, querySelectorAll() { return []; }};
    }
    const form = elements.databaseSettings;
    form.elements = {
        configType: {value: 'BUILTIN', addEventListener() {}},
        dbBackupInterval: {options: [{value: '-1'}]},
        dbBackupRetentionCount: {options: [{value: '-1'}]}
    };
    let report;
    const context = {
        document: {getElementById: id => elements[id], querySelectorAll: () => []},
        watchDatabaseStatus(options, callback) {
            assert.equal(options.url, '/rest/getDatabaseStatus');
            assert.equal(options.csrfHeader, 'X-CSRF');
            assert.equal(options.csrfToken, 'token');
            report = callback;
        },
        fetch: async () => ({ok: true, json: async () => ({'subsonic-response': {
            status: 'ok', databaseSettings: {configType: 'BUILTIN', dbBackupInterval: -1, dbBackupRetentionCount: -1, backuppable: true}
        }})})
    };
    vm.runInNewContext(fs.readFileSync('airsonic-main/src/main/webapp/script/database-settings.js', 'utf8'), context);
    context.initDatabaseSettings({baseUrl: '/rest/', csrfHeader: 'X-CSRF', csrfToken: 'token'});
    await new Promise(setImmediate);
    assert.equal(elements.databaseFields.disabled, false);
    assert.equal(elements.databaseActions.disabled, false);
    const idle = {version: 0, running: false, message: ''};
    report({backup: {version: 1, running: true, message: 'Backing up'}, export: idle, import: idle});
    assert.equal(elements.databaseOperation.textContent, 'backup: Backing up');
    assert.equal(elements.backupdb.disabled, true);
    assert.equal(elements.exportdb.disabled, false);
    report({backup: {version: 1, running: false, message: 'Complete'}, export: idle,
        import: {version: 2, running: true, message: '<untrusted>'}});
    assert.equal(elements.databaseOperation.textContent, 'backup: Complete | import: <untrusted>');
    assert.equal(elements.backupdb.disabled, false);
    assert.equal(elements.importdb.disabled, true);
    report({backup: idle, export: idle, import: idle});
    assert.equal(elements.databaseOperation.textContent, '');
    assert.equal(elements.importdb.disabled, false);
});
