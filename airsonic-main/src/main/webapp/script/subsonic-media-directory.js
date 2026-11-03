function createMediaDirectory(options) {
    var generation = 0;
    function tableEntry(entry) {
        // REST omits null JAXB fields; DataTables expects every column to exist.
        return Object.assign({
            trackNumber: null, discNumber: null, title: null, album: null, artist: null,
            albumArtist: null, genre: null, year: null, format: null, fileSize: null,
            duration: null, bitRate: null, playCount: null, lastPlayed: null,
            lastScanned: null, created: null, changed: null, entryType: null,
            dimensions: null, starred: false
        }, entry);
    }
    return {
        get: async function (ids, paths) {
            var current = ++generation;
            var form = new URLSearchParams({f: 'json', c: 'airsonic-web', includeWebsiteDetails: 'true'});
            (ids || []).forEach(function (id) { form.append('id', id); });
            Object.keys(paths || {}).forEach(function (folder) {
                (paths[folder] || []).forEach(function (path) { form.append('folderPath', folder + ':' + path); });
            });
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url, {method: 'POST', credentials: 'same-origin', headers: headers, body: form});
            if (!response.ok || response.redirected) throw new Error('Directory request failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok' || !body.websiteDirectory) throw new Error((body && body.error && body.error.message) || 'Directory request failed');
            if (current !== generation) return null;
            var directory = body.websiteDirectory;
            if (!directory.detail) return {contentType: directory.contentType};
            return Object.assign({}, directory.detail, {
                contentType: directory.contentType, comment: directory.comment,
                userRating: directory.userRating, averageRating: directory.averageRating,
                musicBrainzReleaseId: directory.musicBrainzReleaseId,
                files: (directory.file || []).map(tableEntry), subDirs: (directory.subDir || []).map(tableEntry),
                ancestors: directory.ancestor || [], siblingAlbums: (directory.siblingAlbum || []).map(tableEntry)
            });
        }
    };
}
