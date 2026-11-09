/* The video page obtains playback metadata from Ktor rather than a JSP model. */
function saveVideoBookmark(media, model, api) {
    if (!model.autoBookmark || !api) return;
    const seconds = Math.round(media.currentTime);
    if (Number.isFinite(seconds) && model.position !== seconds * 1000 && seconds % model.videoBookmarkFrequency === 0) {
        model.position = seconds * 1000;
        api.create(model.videoId, model.position, 'Played on Web Video Player');
    }
}

async function loadVideoPlayer(doc, dependencies = {}) {
    const view = doc.defaultView;
    const request = dependencies.fetch || view.fetch.bind(view);
    const createPlayer = dependencies.createPlayer || ((id, options) => new view.MediaElementPlayer(id, options));
    const config = doc.getElementById('videoPlayerData').dataset;
    const element = doc.getElementById('videoPlayer');
    function url(value) {
        const parsed = new URL(value, doc.baseURI);
        if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Invalid playback URL');
        return parsed.href;
    }
    async function read(address, credentials = 'same-origin') {
        const response = await request(address, {cache: 'no-store', credentials});
        if (!response.ok || response.redirected) throw new Error('Video unavailable');
        return response.json();
    }
    try {
        const endpoint = new URL(config.url, doc.baseURI);
        const page = new URL(view.location.href);
        for (const name of ['id', 'position', 'player']) {
            if (page.searchParams.has(name)) endpoint.searchParams.set(name, page.searchParams.get(name));
        }
        const model = await read(endpoint.href);
        if (!Number.isInteger(model.videoId) || !model.streamUrls || !model.streamUrls[model.defaultBitRate] ||
            !Array.isArray(model.captions)) throw new Error('Invalid video data');
        const sources = Object.entries(model.streamUrls).map(([quality, address]) => {
            const source = doc.createElement('source'); source.src = url(address);
            source.setAttribute('data-quality', quality); source.type = model.contentType;
            return source;
        });
        const tracks = model.captions.map(caption => {
            const track = doc.createElement('track'); track.kind = 'subtitles'; track.src = url(caption.url);
            track.label = caption.identifier + ' (' + caption.language + ')'; track.srclang = caption.language;
            return track;
        });
        model.remoteStreamUrl = url(model.remoteStreamUrl);
        model.remoteCoverArtUrl = url(model.remoteCoverArtUrl);
        model.remoteCaptionsListUrl = url(model.remoteCaptionsListUrl);
        model.remoteCaptions = []; model.currentUrl = model.defaultBitRate;
        view.videoModel = model; // Read by the existing Chromecast plugin.
        sources.concat(tracks).forEach(child => element.appendChild(child));
        element.setAttribute('type', model.contentType);
        doc.getElementById('videoTitle').textContent = model.videoTitle || '';
        const target = address => { const parsed = new URL(address, doc.baseURI); parsed.searchParams.set('id', model.videoId); return parsed.href; };
        doc.getElementById('videoBack').href = target(config.backUrl);
        for (const [id, allowed, address] of [['share', model.canShare, config.shareUrl], ['download', model.canDownload, config.downloadUrl]]) {
            const button = doc.getElementById(id); button.hidden = !allowed;
            if (allowed) button.onclick = () => {view.location.href = target(address);};
        }
        const mutationError = () => {doc.getElementById('videoError').textContent = 'Could not update video.';};
        if (view.createSubsonicStars) view.setMediaFilesStarred = view.createSubsonicStars({
            starUrl: config.starUrl, unstarUrl: config.unstarUrl, csrfHeader: model.csrfHeader, csrfToken: model.csrfToken,
            onError: mutationError
        });
        if (view.createSubsonicBookmarks) view.bookmarkApi = view.createSubsonicBookmarks({
            listUrl: config.bookmarksUrl, createUrl: config.bookmarkCreateUrl, deleteUrl: config.bookmarkDeleteUrl,
            csrfHeader: model.csrfHeader, csrfToken: model.csrfToken, onError: mutationError
        });
        const star = doc.getElementById('starImage'); star.hidden = false;
        star.src = model.starred ? config.starOn : config.starOff;
        star.onclick = () => {
            const value = !model.starred;
            (dependencies.setStar || view.setMediaFilesStarred)([model.videoId], value, () => {
                model.starred = value; star.src = value ? config.starOn : config.starOff;
            });
        };
        // Casting captions are optional and must not delay or break local playback.
        // JWT requests must not reset the browser session's CSRF token.
        read(model.remoteCaptionsListUrl, 'omit').then(captions => {
            if (Array.isArray(captions)) model.remoteCaptions = captions.map(caption => ({...caption, url: url(caption.url)}));
        }).catch(() => {});
        return createPlayer('videoPlayer', {
            alwaysShowControls: true, enableKeyboard: true, useDefaultControls: true, enableTracks: true,
            castAppID: '4FBFE470', features: ['speed', 'quality', 'chromecast'],
            hls: {path: config.hlsUrl}, dash: {path: config.dashUrl},
            defaultSpeed: '1.00', speeds: ['8.00', '2.00', '1.50', '1.25', '1.00', '0.75', '0.5'],
            defaultQuality: model.defaultBitRate, videoWidth: '100%', videoHeight: '100%',
            qualityChangeCallback: (media, node, quality) => {model.currentUrl = quality;},
            success(media, node, instance) {
                view.videoPlayer = instance;
                if (model.streamable) {
                    instance.media.rendererName = 'html5';
                    instance.buildspeed(instance, instance.getElement(instance.controls), instance.getElement(instance.layers), instance.media);
                }
                let resumed = false;
                const resume = () => {
                    if (resumed) return;
                    try { instance.setCurrentTime(model.position / 1000); resumed = true; } catch (_) { /* Wait for metadata. */ }
                };
                media.addEventListener('loadedmetadata', resume);
                for (const event of ['ended', 'timeupdate', 'seeked', 'pause']) {
                    media.addEventListener(event, () => {
                        if (resumed) saveVideoBookmark(media, model, dependencies.bookmarks || view.bookmarkApi);
                    });
                }
                instance.setSrc(url(model.streamUrls[model.defaultBitRate]));
                if (media.readyState >= 1) resume();
            }
        });
    } catch (_) {
        doc.getElementById('videoError').textContent = config.error || 'Video unavailable.';
    }
}
if (typeof module !== 'undefined') module.exports = {loadVideoPlayer, saveVideoBookmark};
