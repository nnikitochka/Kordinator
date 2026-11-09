function bindFileUpload(form, results, options) {
    var pending = false;
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (pending) return;
        pending = true;
        var button = form.querySelector('[type=submit]');
        button.disabled = true;
        results.textContent = '';
        function show(message) {
            var row = document.createElement('p');
            row.textContent = message;
            results.appendChild(row);
        }
        try {
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url + '?f=json&c=airsonic-web', {
                method: 'POST', credentials: 'same-origin', cache: 'no-store', headers: headers,
                body: new FormData(form)
            });
            if (!response.ok || response.redirected) throw new Error('Upload failed');
            var body = (await response.json())['subsonic-response'];
            if (!body) throw new Error('Invalid upload response');
            var result = body.uploadResult || {};
            (result.uploadedFile || []).forEach(function (file) { show(options.uploaded.replace('{file}', function () { return file; })); });
            (result.unzippedFile || []).forEach(function (file) { show(options.extracted.replace('{file}', function () { return file; })); });
            (result.error || []).forEach(show);
            if (body.status !== 'ok') throw new Error((body.error && body.error.message) || 'Upload failed');
            if (!body.uploadResult) throw new Error('Missing upload result');
        } catch (error) {
            show(error.message);
        } finally {
            pending = false;
            button.disabled = false;
        }
    });
}
