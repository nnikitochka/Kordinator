/* Public share pages load their data from the signed Ktor endpoint. */
async function loadPublicShare(doc, dependencies = {}) {
    const request = dependencies.fetch || fetch;
    const createPlayer = dependencies.createPlayer || ((id, options) => new MediaElementPlayer(id, options));
    const title = doc.getElementById('shareTitle');
    const subtitle = doc.getElementById('shareSubtitle');
    const escape = value => String(value || '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch])); // The playlist plugin builds HTML from these text fields.
    function url(value) {
        const parsed = new URL(value, doc.baseURI);
        if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Invalid media URL');
        return parsed.href;
    }
    try {
        const response = await request(doc.getElementById('publicShareData').dataset.url, {cache: 'no-store', credentials: 'same-origin'});
        if (!response.ok || response.redirected) throw new Error('Share unavailable');
        const data = await response.json();
        if (!data.available || !Array.isArray(data.media) || !data.media.length) throw new Error('Share unavailable');
        const playlist = data.media.map(song => ({
            src: url(song.streamUrl), title: escape(song.title), type: song.contentType,
            'data-playlist-thumbnail': url(song.coverArtUrl),
            'data-playlist-description': escape(song.artist), 'data-playlist-caption': url(song.captionsUrl)
        }));
        title.textContent = data.description || data.media[0].artist || '';
        subtitle.textContent = data.description ? (data.username || '') : (data.media[0].album || '');
        for (const [property, content] of [['og:title', title.textContent], ['og:image', playlist[0]['data-playlist-thumbnail']]]) {
            let meta = doc.querySelector('meta[property=\"' + property + '\"]');
            if (!meta) { meta = doc.createElement('meta'); meta.setAttribute('property', property); doc.head.appendChild(meta); }
            meta.setAttribute('content', content);
        }
        const element = doc.createElement(data.videoPresent ? 'video' : 'audio');
        element.id = 'player'; element.setAttribute('controls', ''); element.setAttribute('preload', 'none');
        if (data.videoPresent) element.style.cssText = 'width:100%;height:100%';
        doc.getElementById('sharePlayer').appendChild(element);
        return createPlayer('player', {
            useDefaultControls: true,
            features: ['speed', 'quality', 'chromecast', 'playlist', 'prevtrack', 'nexttrack', 'shuffle', 'loop'],
            currentMessage: '', defaultSpeed: '1.00', speeds: ['8.00', '2.00', '1.50', '1.25', '1.00', '0.75', '0.5'],
            castTitle: 'Airsonic', castAppID: '4FBFE470', castPolicy: 'tab', castEnableTracks: true,
            playlistTitle: escape(data.description), playlist, audioWidth: 600,
            success: function(media, node, player) {
                $(player.playlistLayer).on('newplaylistsrc', async event => {
                    media.originalNode.setAttribute('type', event.detail.type);
                    player.buildspeed(player, player.getElement(player.controls), player.getElement(player.layers), media);
                    try {
                        const response = await request(url(event.detail['data-playlist-caption']));
                        if (!response.ok || response.redirected) return;
                        const captions = await response.json();
                        player.trackFiles = captions.map(caption => {
                            const track = doc.createElement('track'); track.kind = 'subtitles';
                            track.label = caption.identifier + ' (' + caption.language + ')';
                            track.src = url(caption.url); track.srclang = caption.language;
                            return track;
                        });
                        player.rebuildtracks();
                    } catch (_) { /* Captions are optional. */ }
                });
            }
        });
    } catch (_) {
        title.textContent = 'Sorry, the content is not available.'; subtitle.textContent = '';
    }
}
if (typeof module !== 'undefined') module.exports = {loadPublicShare};
