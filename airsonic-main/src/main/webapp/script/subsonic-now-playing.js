/* Browser-session access to the shared now-playing API. */
function watchSubsonicNowPlaying(options, callback) {
    var disposed = false;
    var generation = 0;
    var topics = {};
    var paths = ['/topic/nowPlaying/current/add', '/topic/nowPlaying/current/remove',
        '/topic/nowPlaying/recent/add', '/topic/nowPlaying/recent/remove'];
    function refresh() {
        if (disposed) return;
        var request = ++generation;
        var form = new URLSearchParams({f: 'json', c: 'airsonic-web', includeWebsiteDetails: 'true'});
        var headers = {};
        headers[options.csrfHeader] = options.csrfToken;
        fetch(options.baseUrl + 'getNowPlaying', {
            method: 'POST', credentials: 'same-origin', headers: headers, body: form
        }).then(function (response) {
            if (!response.ok || response.redirected) throw new Error('Now playing request failed');
            return response.json();
        }).then(function (data) {
            var body = data['subsonic-response'];
            if (!body || body.status !== 'ok' || !body.nowPlaying) throw new Error('Invalid now playing response');
            if (disposed || request !== generation) return;
            var entries = (body.nowPlaying.entry || []).map(function (entry) {
                return Object.assign({}, entry, {
                    mediaFileId: entry.id, username: entry.displayUsername,
                    artist: entry.displayArtist, title: entry.displayTitle
                });
            });
            callback(entries.filter(function (entry) { return entry.active === true; }),
                entries.filter(function (entry) { return entry.active === false; }));
        }).catch(function (error) {
            if (!disposed && request === generation) console.error(error);
        });
    }
    paths.forEach(function (path) { topics[path] = refresh; });
    function unsubscribe() {
        paths.forEach(function (path) { options.stomp.unsubscribe(path, options.owner); });
    }
    options.stomp.subscribe(options.owner, topics, function () {
        if (disposed) return unsubscribe();
        refresh();
        options.stomp.onConnect.push(refresh);
    });
    window.addEventListener('pagehide', function () {
        disposed = true;
        var index = options.stomp.onConnect.indexOf(refresh);
        if (index !== -1) options.stomp.onConnect.splice(index, 1);
        unsubscribe();
    }, {once: true});
    return refresh;
}
