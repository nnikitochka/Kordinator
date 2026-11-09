function createWebsiteSettings(options) {
    var pending = Promise.resolve();
    var localChanges = 0;
    function request(url, name, value) {
        pending = pending.catch(function () {}).then(async function () {
            var form = new URLSearchParams({f: 'json', c: 'airsonic-web'});
            if (name) form.set(name, String(value));
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(url, {
                method: 'POST', credentials: 'same-origin', headers: headers, body: form
            });
            if (!response.ok || response.redirected) throw new Error('Settings request failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Settings request failed');
            return body;
        });
        return pending;
    }
    return {
        set: function (name, value) {
            localChanges++;
            var result = request(options.url, name, value);
            result.catch(function (error) { console.error(error); });
            return result;
        },
        watch: function (name, callback) {
            var disposed = false, timer;
            function poll() {
                if (disposed) return;
                var changes = localChanges;
                request(options.readUrl).then(function (body) {
                    var settings = body.websiteSettings;
                    if (!settings || typeof settings[name] !== 'boolean') throw new Error('Invalid settings response');
                    if (!disposed && changes === localChanges) callback(settings[name], settings);
                }).catch(function (error) { if (!disposed) console.error(error); }).then(function () {
                    if (!disposed) timer = window.setTimeout(poll, 20000);
                });
            }
            poll();
            window.addEventListener('pagehide', function () {
                disposed = true;
                window.clearTimeout(timer);
            }, {once: true});
        }
    };
}
