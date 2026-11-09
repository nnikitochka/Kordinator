function bindMediaFileComment(form, errorBox, options) {
    var pending = false;
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (pending) return;
        pending = true;
        var button = form.querySelector('[type=submit]');
        button.disabled = true;
        errorBox.textContent = '';
        var fields = new URLSearchParams(new FormData(form));
        fields.set('f', 'json');
        fields.set('c', 'airsonic-web');
        try {
            var headers = {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url, {
                method: 'POST', credentials: 'same-origin', cache: 'no-store', headers: headers, body: fields.toString()
            });
            if (!response.ok || response.redirected) throw new Error('Comment update failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Comment update failed');
            options.onSaved(fields.get('id'), fields.get('comment'));
        } catch (error) {
            errorBox.textContent = error.message;
        } finally {
            pending = false;
            button.disabled = false;
        }
    });
}
