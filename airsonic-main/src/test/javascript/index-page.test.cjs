const {test} = require('node:test');
const assert = require('node:assert/strict');
const {resolveIndexMainTarget} = require('../../main/webapp/script/index-page.js');

const root = 'https://media.example/airsonic/';
const current = 'https://media.example/airsonic/index.view';

test('keeps valid local navigation targets inside the application context', () => {
    assert.equal(resolveIndexMainTarget('personalSettings.view?saved=true', root, current),
        'https://media.example/airsonic/personalSettings.view?saved=true');
    assert.equal(resolveIndexMainTarget('', root, current), 'https://media.example/airsonic/nowPlaying.view');
});

test('rejects executable, cross-origin, and context-escaping navigation targets', () => {
    for (const value of ['javascript:alert(1)', '//attacker.example/page', 'https://attacker.example/page',
        '/outside/page', '../outside/page', 'foo%2f..%2f..%2foutside', 'foo\\..\\outside']) {
        assert.equal(resolveIndexMainTarget(value, root, current), 'https://media.example/airsonic/nowPlaying.view', value);
    }
});
