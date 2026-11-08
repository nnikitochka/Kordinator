function watchUploadStatus(options, onUpdate) {
    var disposed = false, timer;
    function poll() {
        if (disposed) return;
        var form = new URLSearchParams({f: 'json', c: 'airsonic-web'});
        var headers = {};
        headers[options.csrfHeader] = options.csrfToken;
        fetch(options.url, {method: 'POST', credentials: 'same-origin', headers: headers, body: form})
            .then(function (response) {
                if (!response.ok || response.redirected) throw new Error('Upload status request failed');
                return response.json();
            }).then(function (json) {
                var body = json['subsonic-response'];
                if (!body || body.status !== 'ok' || !body.uploadStatuses) throw new Error('Invalid upload status response');
                var uploads = body.uploadStatuses.upload || [];
                if (!Array.isArray(uploads) || uploads.some(function (upload) {
                    return !upload || !/^[0-9a-f-]{36}$/i.test(upload.transferId) ||
                        !Number.isFinite(upload.bytesUploaded) || !Number.isFinite(upload.bytesTotal);
                })) throw new Error('Invalid upload status');
                if (!disposed) onUpdate(uploads);
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
