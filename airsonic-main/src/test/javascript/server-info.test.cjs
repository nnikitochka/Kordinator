const test = require('node:test');
const assert = require('node:assert/strict');
const {loadServerInfo} = require('../../main/webapp/script/server-info.js');
function element() {
    const item = {dataset: {}, hidden: true, children: [], attrs: {}, textContent: '',
        appendChild(child) {this.children.push(child);}, replaceChildren() {this.children = [];},
        removeAttribute(name) {delete this.attrs[name]; delete this[name];}, addEventListener(name, fn) {this[name] = fn;}};
    Object.defineProperty(item, 'innerHTML', {set() {throw new Error('Unsafe HTML insertion');}});
    return item;
}
function fixture() {
    const nodes = Object.fromEntries(['serverInfoData', 'serverInfoError', 'serverLogSection', 'serverLogError', 'serverLogEntries',
        'serverLogFile', 'serverUpgrade', 'serverTitle', 'serverVersion', 'serverBuildDate', 'serverBuildNumber', 'serverRuntime',
        'serverLatestVersion', 'serverRefresh'].map(id => [id, element()]));
    nodes.serverInfoData.dataset = {infoUrl: '/airsonic/rest/serverInfo', logUrl: '/airsonic/rest/serverLog', unknown: '(Unknown)'};
    nodes.serverTitle.dataset.template = 'About {0}';
    const brands = [element(), element()], link = element(); link.onclick = 'window.open';
    nodes.serverUpgrade.querySelectorAll = () => [link];
    const doc = {defaultView: {}, getElementById: id => nodes[id], querySelectorAll: () => brands, createElement: element};
    return {doc, nodes, brands, link, calls: []};
}
function info(extras = {}) {
    return {brand: 'Музыка <img>', localVersion: '11.0', buildDate: '2026-11-09T00:00:00Z', buildNumber: 'abcdef1234567',
        serverInfo: 'Tomcat, java 25, Linux', usedMemory: 1024 * 1024, totalMemory: 2 * 1024 * 1024 * 1024,
        newVersionAvailable: true, latestVersion: '12.0 <script>', latestVersionUrl: 'https://example.test/releases/12', canReadLog: true, ...extras};
}
const log = {available: true, logFile: '/home/Журнал <img>.log', logEntries: ['Первая <script>alert(1)</script>', 'Последняя & строка']};
const response = data => ({ok: true, json: async () => data});
async function load(results) {
    const state = fixture();
    await loadServerInfo(state.doc, {fetch: async (url, options) => {
        state.calls.push({url, options});
        const result = results.shift();
        if (result instanceof Error) throw result;
        return typeof result === 'function' ? result() : result;
    }});
    state.refresh = () => state.nodes.serverRefresh.click({preventDefault() {state.prevented = true;}});
    return state;
}
test('loads server info and admin log through REST and renders untrusted strings as text', async () => {
    const state = await load([response(info()), response(log)]);
    assert.deepEqual(state.calls.map(call => call.url), ['/airsonic/rest/serverInfo', '/airsonic/rest/serverLog']);
    state.calls.forEach(call => {assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.cache, 'no-store');});
    assert.equal(state.nodes.serverTitle.textContent, 'About Музыка <img>');
    assert.ok(state.brands.every(node => node.textContent === 'Музыка <img>'));
    assert.equal(state.nodes.serverVersion.textContent, '11.0');
    assert.equal(state.nodes.serverBuildNumber.href, 'https://github.com/airsonic-advanced/airsonic-advanced/commit/abcdef1234567');
    assert.equal(state.nodes.serverRuntime.textContent, 'Tomcat, java 25, Linux (1.0 MB / 2.00 GB)');
    assert.equal(state.nodes.serverLatestVersion.textContent, '12.0 <script>');
    assert.equal(state.link.href, 'https://example.test/releases/12');
    assert.equal(state.link.onclick, undefined); assert.equal(state.link.rel, 'noopener noreferrer');
    assert.equal(state.nodes.serverLogFile.textContent, log.logFile);
    assert.deepEqual(state.nodes.serverLogEntries.children.map(row => row.children[0].textContent), log.logEntries);
    assert.equal(state.nodes.serverLogSection.hidden, false);
    assert.equal(state.nodes.serverLogError.hidden, true);
    assert.equal(state.nodes.serverInfoError.hidden, true);
});
test('ordinary user never requests the log and refresh clears old privileged content', async () => {
    const state = await load([response(info()), response(log), response(info({canReadLog: false, newVersionAvailable: false}))]);
    await state.refresh();
    assert.equal(state.prevented, true);
    assert.equal(state.calls.length, 3);
    assert.equal(state.nodes.serverLogSection.hidden, true);
    assert.equal(state.nodes.serverLogEntries.children.length, 0);
    assert.equal(state.nodes.serverLogFile.textContent, '');
    assert.equal(state.nodes.serverUpgrade.hidden, true);
});
test('unknown metadata and unsafe update URLs are handled without creating unsafe links', async () => {
    for (const url of ['javascript:alert(1)', 'data:text/html,bad', '//example.test/releases', 'invalid']) {
        const state = await load([response(info({canReadLog: false, latestVersionUrl: url, buildNumber: '<img>', localVersion: null, buildDate: 'invalid', usedMemory: -1}))]);
        assert.equal(state.nodes.serverUpgrade.hidden, true);
        assert.equal(state.nodes.serverBuildNumber.href, undefined);
        assert.equal(state.nodes.serverBuildNumber.textContent, '<img>');
        assert.equal(state.nodes.serverVersion.textContent, '(Unknown)');
        assert.equal(state.nodes.serverBuildDate.textContent, '(Unknown)');
        assert.match(state.nodes.serverRuntime.textContent, /\(\(Unknown\) \/ 2.00 GB\)/);
        assert.equal(state.calls.length, 1);
    }
});
test('snapshot failures show an error and never request the log', async () => {
    for (const failure of [new Error('network'), {ok: false}, {ok: true, redirected: true}, response(null), response({})]) {
        const state = await load([failure]);
        assert.equal(state.nodes.serverInfoError.hidden, false);
        assert.equal(state.nodes.serverLogSection.hidden, true);
        assert.equal(state.calls.length, 1);
    }
});
test('missing log or rejected log request does not hide server info', async () => {
    for (const failure of [new Error('network'), {ok: false}, response({available: false, logEntries: [], logFile: '/missing.log'}), response({})]) {
        const state = await load([response(info()), failure]);
        assert.equal(state.nodes.serverInfoError.hidden, true);
        assert.equal(state.nodes.serverLogSection.hidden, false);
        assert.equal(state.nodes.serverLogError.hidden, false);
        assert.equal(state.nodes.serverVersion.textContent, '11.0');
    }
});
test('refresh recovers after errors and suppresses concurrent refresh requests', async () => {
    let complete;
    const pending = new Promise(resolve => {complete = resolve;});
    const state = await load([new Error('offline'), () => pending]);
    const refresh = state.refresh();
    await state.refresh();
    assert.equal(state.calls.length, 2);
    complete(response(info({canReadLog: false})));
    await refresh;
    assert.equal(state.nodes.serverInfoError.hidden, true);
    assert.equal(state.nodes.serverVersion.textContent, '11.0');
});
