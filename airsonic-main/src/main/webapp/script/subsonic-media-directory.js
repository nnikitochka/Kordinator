function createMediaDirectory(options) {
    var generation = 0;
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
                files: directory.file || [], subDirs: directory.subDir || [],
                ancestors: directory.ancestor || [], siblingAlbums: directory.siblingAlbum || []
            });
        }
    };
}
