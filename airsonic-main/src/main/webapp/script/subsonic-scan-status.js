/* Poll the existing scan-status endpoint for the website's right panel. */
function watchSubsonicScanStatus(options, onStatus) {
    var stopped = false;
    var timer;

    async function poll() {
        try {
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url, {
                method: 'POST', credentials: 'same-origin', headers: headers,
                body: new URLSearchParams({f: 'json', c: 'airsonic-web'})
            });
            if (!response.ok || response.redirected) throw new Error('Scan status request failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok' || !body.scanStatus ||
                    typeof body.scanStatus.scanning !== 'boolean' || typeof body.scanStatus.count !== 'number') {
                throw new Error('Invalid scan status response');
            }
            if (!stopped) onStatus(body.scanStatus);
        } catch (error) {
            if (!stopped) console.error(error);
        } finally {
            if (!stopped) timer = setTimeout(poll, 5000);
        }
    }

    window.addEventListener('pagehide', function () {
        stopped = true;
        clearTimeout(timer);
    }, {once: true});
    poll();
}
