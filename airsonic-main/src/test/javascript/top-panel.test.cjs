const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('renders an untrusted identity as text and updates avatar and settings access', () => {
    const elements = {};
    for (const id of ['top-username', 'top-settings', 'top-avatar-link', 'top-avatar', 'top-default-avatar']) {
        elements[id] = {
            attributes: {},
            setAttribute(name, value) { this.attributes[name] = value; },
            removeAttribute(name) { delete this.attributes[name]; },
            set innerHTML(value) { assert.fail('identity must be rendered as text'); }
        };
    }
    const document = {getElementById: id => elements[id]};
    const context = {};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/script/top-panel.js'), 'utf8'), context);
    const name = '<img src=x onerror=alert(1)>&user';
    context.renderTopPanel(document, {username: name, settingsRole: true, showAvatar: true}, '/airsonic/avatar.view');
    assert.equal(elements['top-username'].textContent, name);
    assert.equal(elements['top-settings'].hidden, false);
    assert.equal(elements['top-username'].attributes.href, 'personalSettings.view');
    assert.equal(elements['top-avatar'].attributes.src, '/airsonic/avatar.view?username=' + encodeURIComponent(name));
    assert.equal(elements['top-avatar'].hidden, false);
    assert.equal(elements['top-default-avatar'].hidden, true);
    context.renderTopPanel(document, {username: 'restricted', settingsRole: false, showAvatar: false}, '/airsonic/avatar.view');
    assert.equal(elements['top-username'].textContent, 'restricted');
    assert.equal(elements['top-settings'].hidden, true);
    assert.equal(elements['top-username'].attributes.href, undefined);
    assert.equal(elements['top-avatar-link'].attributes.href, undefined);
    assert.equal(elements['top-avatar'].attributes.src, undefined);
    assert.equal(elements['top-avatar'].hidden, true);
    assert.equal(elements['top-default-avatar'].hidden, false);
});
