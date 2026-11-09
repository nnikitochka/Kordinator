function bindRandomQueue(form, errorElement, options) {
    var pending = false;
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (pending) return;
        var submitter = event.submitter || form.querySelector('[type=submit]');
        var params = new URLSearchParams(new FormData(form));
        if (submitter && submitter.name) params.set(submitter.name, submitter.value);
        params.set('f', 'json');
        params.set('c', 'airsonic-web');
        var player = options.player();
        if (player != null) params.set('playerId', player);
        var buttons = Array.from(form.querySelectorAll('[type=submit]'));
        pending = true;
        buttons.forEach(function (button) { button.disabled = true; });
        errorElement.hidden = true;
        errorElement.textContent = '';
        try {
            options.save();
            var headers = {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url, {
                method: 'POST', credentials: 'same-origin', cache: 'no-store', headers: headers, body: params.toString()
            });
            if (!response.ok || response.redirected) throw new Error('Random queue request failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Random queue request failed');
            options.updated(player);
        } catch (error) {
            errorElement.textContent = error.message;
            errorElement.hidden = false;
        } finally {
            pending = false;
            buttons.forEach(function (button) { button.disabled = false; });
        }
    });
}
