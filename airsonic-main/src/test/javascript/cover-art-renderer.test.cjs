const test = require('node:test');
const assert = require('node:assert/strict');
const createCoverArtRenderer = require('../../main/webapp/script/cover-art-renderer.js');

class Element {
    constructor(tagName) {
        this.tagName = tagName;
        this.children = [];
        this.style = {};
        this.attributes = {};
        this.listeners = {};
        this.className = '';
    }

    set textContent(value) {
        this.children = [];
        this._textContent = String(value);
    }

    get textContent() {
        return (this._textContent || '') + this.children.map(child => child.textContent).join('');
    }

    appendChild(child) {
        child.parentNode = this;
        this.children.push(child);
        return child;
    }

    insertBefore(child, sibling) {
        child.parentNode = this;
        const index = sibling ? this.children.indexOf(sibling) : -1;
        this.children.splice(index < 0 ? this.children.length : index, 0, child);
        return child;
    }

    get firstChild() { return this.children[0] || null; }
    get childNodes() { return this.children; }
    get nextSibling() {
        if (!this.parentNode) return null;
        const siblings = this.parentNode.children;
        return siblings[siblings.indexOf(this) + 1] || null;
    }

    addEventListener(name, listener) { this.listeners[name] = listener; }
}

function setup() {
    const document = {
        createElement: tag => new Element(tag),
        createTextNode: text => {
            const node = new Element('#text');
            node.textContent = text;
            return node;
        }
    };
    return createCoverArtRenderer({
        coverArtUrl: '/airsonic/coverArt.view',
        changeCoverArtUrl: '/airsonic/changeCoverArt.view',
        albumPageUrl: '/airsonic/main.view',
        playlistPageUrl: '/airsonic/playlist.view',
        playOverlayUrl: '/airsonic/icons/play.svg',
        addOverlayUrl: '/airsonic/icons/add.svg',
        changeLabel: 'Change',
        zoomLabel: 'Zoom',
        playlistSongsLabel: 'songs'
    }, document);
}

function find(node, predicate) {
    if (predicate(node)) return node;
    for (const child of node.children) {
        const match = find(child, predicate);
        if (match) return match;
    }
    return null;
}

test('builds album URLs with encoded ids and preserves the zoom and change links', () => {
    const renderer = setup();
    const card = renderer.render({albumId: '7&x=8', size: 160, showZoom: true, showChange: true});
    const image = find(card, node => node.tagName === 'img' && node.alt !== 'Play' && node.alt !== 'Add');
    const anchors = [];
    (function collect(node) {
        if (node.tagName === 'a') anchors.push(node);
        node.children.forEach(collect);
    }(card));
    assert.equal(image.src, '/airsonic/coverArt.view?size=160&id=7%26x%3D8');
    assert.equal(anchors[0].href, '/airsonic/coverArt.view?id=7%26x%3D8');
    assert.equal(anchors[1].href, '/airsonic/changeCoverArt.view?id=7%26x%3D8');
    assert.equal(anchors[2].href, '/airsonic/coverArt.view?id=7%26x%3D8');
});

test('renders playlist captions as text and uses playlist cover and navigation routes', () => {
    const renderer = setup();
    const unsafeCaption = '<img src=x onerror=alert(1)>';
    const card = renderer.render({playlistId: 12, size: 160, showLink: true, captionCount: 2,
        caption1: unsafeCaption, caption2: '3 songs – 00:12'});
    const image = find(card, node => node.tagName === 'img' && node.alt === unsafeCaption);
    const captionLink = find(card, node => node.tagName === 'a' && node.textContent === unsafeCaption);
    assert.equal(image.src, '/airsonic/coverArt.view?size=160&id=pl-12');
    assert.equal(captionLink.href, '/airsonic/playlist.view?id=12');
    assert.equal(captionLink.textContent, unsafeCaption);
    assert.equal(card.textContent.includes(unsafeCaption), true);
    assert.equal(find(card, node => node.tagName === 'img').alt, 'Play');
});

test('play and add overlays dispatch to the matching album or playlist queue actions', () => {
    const previousTop = global.top;
    const actions = [];
    global.top = {playQueue: {
        onPlay: id => actions.push(['play-album', id]),
        onAdd: id => actions.push(['add-album', id]),
        onPlayPlaylist: id => actions.push(['play-playlist', id]),
        onAddPlaylist: id => actions.push(['add-playlist', id])
    }};
    try {
        const renderer = setup();
        const album = renderer.render({albumId: 23, size: 100});
        const playlist = renderer.render({playlistId: 42, size: 100});
        const albumOverlays = album.children[0].children.slice(0, 2).map(wrapper => wrapper.firstChild);
        const playlistOverlays = playlist.children[0].children.slice(0, 2).map(wrapper => wrapper.firstChild);
        const event = {preventDefault() {}, stopPropagation() {}};
        albumOverlays[0].listeners.click(event);
        albumOverlays[1].listeners.click(event);
        playlistOverlays[0].listeners.click(event);
        playlistOverlays[1].listeners.click(event);
        assert.deepEqual(actions, [
            ['play-album', 23], ['add-album', 23], ['play-playlist', 42], ['add-playlist', 42]
        ]);
    } finally {
        global.top = previousTop;
    }
});

test('encodes caption punctuation and applies requested clipping and delayed appearance', () => {
    const renderer = setup();
    const card = renderer.render({albumId: 4, size: 140, hideOverflow: true, appearAfter: 90,
        captionCount: 3, caption1: 'A & B', caption2: '1990', caption3: 'extra'});
    const frame = find(card, node => node.tagName === 'div' && node.style.overflow === 'hidden');
    const caption = find(card, node => node.className === 'caption1');
    assert.equal(frame.style.width, '140px');
    assert.equal(caption.style.width, '124px');
    assert.equal(renderer.withQuery('/coverArt.view', {id: 'A & B'}), '/coverArt.view?id=A%20%26%20B');
});
