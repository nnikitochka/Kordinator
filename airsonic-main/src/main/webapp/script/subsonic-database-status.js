function watchDatabaseStatus(options, onUpdate) {
    var disposed = false, timer;
    function poll() {
        if (disposed) return;
        var headers = {};
        headers[options.csrfHeader] = options.csrfToken;
        fetch(options.url, {method: 'POST', credentials: 'same-origin', headers: headers,
            body: new URLSearchParams({f: 'json', c: 'airsonic-web'})})
            .then(function (response) {
                if (!response.ok || response.redirected) throw new Error('Database status request failed');
                return response.json();
            }).then(function (json) {
                var body = json['subsonic-response'];
                var statuses = body && body.databaseStatuses;
                if (!body || body.status !== 'ok' || !statuses || ['backup', 'export', 'import'].some(function (name) {
                    var status = statuses[name];
                    return !status || !Number.isSafeInteger(status.version) || typeof status.running !== 'boolean' ||
                        typeof status.failed !== 'boolean' || typeof status.message !== 'string';
                })) throw new Error('Invalid database status response');
                if (!disposed) onUpdate(statuses);
            }).catch(function (error) { if (!disposed) console.error(error); }).then(function () {
                if (!disposed) timer = window.setTimeout(poll, 2000);
            });
    }
    poll();
    window.addEventListener('pagehide', function () {
        disposed = true;
        window.clearTimeout(timer);
    }, {once: true});
}
