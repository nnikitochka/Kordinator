/* Website selection adapter; the standard Subsonic createShare contract is unchanged. */
function bindCreateShare(doc, options) {
    const error = doc.getElementById('shareError');
    const retry = doc.getElementById('shareRetry');
    const result = doc.getElementById('shareResult');
    const selection = new URLSearchParams(options.query);
    const fields = new URLSearchParams();
    for (const name of ['id', 'player', 'playlist', 'i']) {
        for (const value of selection.getAll(name)) fields.append(name, value);
    }
    fields.set('f', 'json'); fields.set('c', 'airsonic-web');
    if (selection.has('id')) doc.getElementById('shareBack').href = 'main.view?id=' + encodeURIComponent(selection.get('id'));
    else if (selection.has('playlist')) doc.getElementById('shareBack').href = 'playlist.view?id=' + encodeURIComponent(selection.get('playlist'));
    let pending = false, created = false;
    async function create() {
        if (pending || created) return;
        pending = true; retry.hidden = true; error.hidden = true; error.textContent = '';
        try {
            const response = await fetch(options.url, {
                method: 'POST', credentials: 'same-origin', cache: 'no-store',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', [options.csrfHeader]: options.csrfToken},
                body: fields.toString()
            });
            if (!response.ok || response.redirected) throw new Error('HTTP ' + response.status);
            const body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok') throw new Error(body && body.error ? body.error.message : 'Invalid REST response');
            const share = body.shares && body.shares.share && body.shares.share[0];
            if (!share || !share.url) throw new Error('Missing share URL');
            const url = new URL(share.url);
            if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Invalid share URL');
            const link = doc.querySelector('#shareLink a');
            link.href = url.href; link.textContent = url.href; link.rel = 'noopener noreferrer';
            doc.querySelectorAll('#shareFacebook a').forEach(a => { a.href = 'https://www.facebook.com/sharer.php?u=' + encodeURIComponent(url.href); });
            doc.querySelectorAll('#shareTwitter a').forEach(a => { a.href = 'https://twitter.com/?status=' + encodeURIComponent('Listening to ' + url.href); });
            created = true; result.hidden = false;
        } catch (failure) {
            error.textContent = failure.message; error.hidden = false; retry.hidden = false;
        } finally { pending = false; }
    }
    retry.addEventListener('click', create);
    return create();
}
