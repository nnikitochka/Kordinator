/* Browser-session access to the shared now-playing API. */
function watchSubsonicNowPlaying(options, callback) {
    var disposed = false;
    var generation = 0;
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
    refresh();
    var timer = window.setInterval(refresh, 5000);
    window.addEventListener('pagehide', function () {
        disposed = true;
        window.clearInterval(timer);
    }, {once: true});
    return refresh;
}
