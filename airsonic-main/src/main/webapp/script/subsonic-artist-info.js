function createArtistInfo(options) {
    var generation = 0;
    return {
        get: async function (id) {
            var current = ++generation;
            var form = new URLSearchParams({f: 'json', c: 'airsonic-web', id: id,
                count: '8', topSongsCount: '100', includeWebsiteDetails: 'true'});
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url, {method: 'POST', credentials: 'same-origin', headers: headers, body: form});
            if (!response.ok || response.redirected) throw new Error('Artist info request failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok' || !body.artistInfo || !body.websiteArtistInfo) {
                throw new Error((body && body.error && body.error.message) || 'Artist info request failed');
            }
            if (current !== generation) return null;
            return {
                similarArtists: (body.artistInfo.similarArtist || []).map(function (artist) {
                    return {mediaFileId: artist.id, artistName: artist.name};
                }),
                artistBio: body.artistInfo,
                topSongs: (body.websiteArtistInfo.topSong || []).map(function (song) {
                    return Object.assign({starred: false, title: null, album: null, artist: null, duration: null}, song);
                })
            };
        }
    };
}
