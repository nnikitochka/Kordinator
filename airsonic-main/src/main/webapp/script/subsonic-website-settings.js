function createWebsiteSettings(options) {
    var pending = Promise.resolve();
    return {
        set: function (name, value) {
            pending = pending.catch(function () {}).then(async function () {
                var form = new URLSearchParams({f: 'json', c: 'airsonic-web'});
                form.set(name, String(value));
                var headers = {};
                headers[options.csrfHeader] = options.csrfToken;
                var response = await fetch(options.url, {
                    method: 'POST', credentials: 'same-origin', headers: headers, body: form
                });
                if (!response.ok || response.redirected) throw new Error('Settings request failed');
                var body = (await response.json())['subsonic-response'];
                if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Settings request failed');
            });
            pending.catch(function (error) { console.error(error); });
            return pending;
        }
    };
}
