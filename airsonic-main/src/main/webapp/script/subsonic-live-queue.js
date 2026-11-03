function createLiveQueue(options) {
    var pending = Promise.resolve();
    var api = {
        send: function (playerId, action, data) {
            pending = pending.catch(function () {}).then(async function () {
                var form = new URLSearchParams({f: 'json', c: 'airsonic-web', playerId: playerId, action: action});
                Object.keys(data || {}).forEach(function (key) {
                    var value = data[key];
                    if (value == null) return;
                    (Array.isArray(value) ? value : [value]).forEach(function (item) { form.append(key, item); });
                });
                var headers = {};
                headers[options.csrfHeader] = options.csrfToken;
                var response = await fetch(options.url, {method: 'POST', credentials: 'same-origin', headers: headers, body: form});
                if (!response.ok || response.redirected) throw new Error('Queue request failed');
                var body = (await response.json())['subsonic-response'];
                if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Queue request failed');
                return body.livePlayQueue;
            });
            pending.catch(function (error) { console.error(error); });
            return pending;
        },
        watch: function (playerId, onQueue) {
            var stopped = false;
            var timer;
            function poll() {
                if (stopped) return;
                api.send(playerId, 'get', {}).then(function (queue) {
                    if (!stopped) onQueue(queue);
                }).catch(function () {}).finally(function () {
                    if (!stopped) timer = window.setTimeout(poll, 5000);
                });
            }
            timer = window.setTimeout(poll, 5000);
            return function () {
                stopped = true;
                window.clearTimeout(timer);
            };
        }
    };
    return api;
}
