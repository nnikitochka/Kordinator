function bindPlaylistImport(form, errorElement, options) {
    var pending = false;
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (pending) return;
        pending = true;
        var button = form.querySelector('[type=submit]');
        button.disabled = true;
        errorElement.hidden = true;
        errorElement.textContent = '';
        try {
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url + '?f=json&c=airsonic-web', {
                method: 'POST', credentials: 'same-origin', cache: 'no-store', headers: headers,
                body: new FormData(form)
            });
            if (!response.ok || response.redirected) throw new Error('Playlist import failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Playlist import failed');
            if (!body.playlistImport || !body.playlistImport.id) throw new Error('Missing imported playlist');
            options.imported(body.playlistImport);
        } catch (error) {
            errorElement.textContent = error.message;
            errorElement.hidden = false;
        } finally {
            pending = false;
            button.disabled = false;
        }
    });
}
