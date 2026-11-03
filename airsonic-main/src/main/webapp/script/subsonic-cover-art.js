function createSubsonicCoverArt(options) {
    async function request(operation, parameters) {
        var form = new URLSearchParams(parameters);
        form.set('f', 'json');
        form.set('c', 'airsonic-web');
        var headers = {};
        headers[options.csrfHeader] = options.csrfToken;
        var response = await fetch(options.baseUrl + operation, {
            method: 'POST', credentials: 'same-origin', headers: headers, body: form
        });
        if (!response.ok || response.redirected) throw new Error('Cover art request failed');
        var body = (await response.json())['subsonic-response'];
        if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Cover art request failed');
        return body;
    }
    return {
        search: async function (artist, album) {
            var body = await request('searchCoverArt', {artist: artist, album: album});
            if (!body.coverArtSearchResults) throw new Error('Invalid cover art response');
            return body.coverArtSearchResults.image || [];
        },
        set: async function (id, url) { await request('setCoverArt', {id: id, url: url}); }
    };
}
