/* Browser-session client for the shared Subsonic playlist API. */
function createSubsonicPlaylists(options) {
    var pending = Promise.resolve();
    var localChanges = 0;
    function request(operation, parameters, onSuccess, onMissing, onFailure) {
        pending = pending.then(async function () {
            var form = new URLSearchParams();
            Object.keys(parameters).forEach(function (key) {
                var values = Array.isArray(parameters[key]) ? parameters[key] : [parameters[key]];
                values.forEach(function (value) { form.append(key, value); });
            });
            form.set("f", "json");
            form.set("c", "airsonic-web");
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.baseUrl + operation, {
                method: "POST", credentials: "same-origin", headers: headers, body: form
            });
            if (!response.ok || response.redirected) throw new Error("Playlist request failed");
            var body = (await response.json())["subsonic-response"];
            if (!body || body.status !== "ok") {
                if (body && body.error && onMissing && (body.error.code === 70 ||
                    (operation === 'getPlaylist' && body.error.code === 50))) return onMissing();
                throw new Error("Subsonic playlist request failed");
            }
            if (operation !== 'getPlaylists' && operation !== 'getPlaylist') localChanges++;
            if (onSuccess) onSuccess(body);
        }).catch(function () {
            options.onError();
            if (onFailure) onFailure();
        });
        return pending;
    }
    function playlist(value) {
        return Object.assign({name: null, duration: null, created: null, changed: null, importedFrom: null}, value, {
            username: value.owner == null ? null : value.owner, shared: value.public == null ? null : value.public, fileCount: value.songCount == null ? null : value.songCount,
            comment: value.comment || "", filesChanged: false
        });
    }
    function entry(value) {
        // Keep the table's display model while using the public REST song identity.
        return Object.assign({title: null, album: null, artist: null, genre: null, year: null, discNumber: null,
            duration: null, created: null, playCount: 0}, value, {
            trackNumber: value.track == null ? null : value.track,
            albumArtist: value.albumArtist || null,
            starred: Boolean(value.starred), present: value.present === true,
            format: value.suffix || null, fileSize: value.formattedFileSize || null,
            bitRate: value.formattedBitRate || null, lastPlayed: value.lastPlayed || null,
            lastScanned: value.lastScanned || null, changed: value.changed || null
        });
    }
    function watch(refresh) {
        var disposed = false;
        var timer;
        function poll() {
            if (disposed) return;
            refresh(function () { return !disposed; }).then(function () {
                if (!disposed) timer = window.setTimeout(poll, 20000);
            });
        }
        poll();
        window.addEventListener('pagehide', function () {
            disposed = true;
            window.clearTimeout(timer);
        }, {once: true});
    }
    var api = {
        list: function (writable, callback) {
            return request('getPlaylists', {writableOnly: writable}, function (body) {
                callback(((body.playlists || {}).playlist || []).map(playlist));
            });
        },
        get: function (id, callback, missing) {
            return request('getPlaylist', {id: id, includeEntryDetails: true}, function (body) {
                if (!body.playlist) throw new Error('Missing playlist');
                callback(playlist(body.playlist), (body.playlist.entry || []).map(entry));
            }, missing);
        },
        create: function (source, playerId, callback) {
            var parameters = {source: source, includePlaylist: true};
            if (playerId != null) parameters.playerId = playerId;
            return request('createPlaylist', parameters, function (body) {
                if (!body.playlist || !body.playlist.id) throw new Error('Missing playlist ID');
                if (callback) callback(body.playlist.id);
            });
        },
        update: function (id, name, comment, shared, callback) {
            return request('updatePlaylist', {playlistId: id, name: name, comment: comment, public: shared}, callback);
        },
        append: function (id, ids, callback) {
            return request('updatePlaylist', {playlistId: id, songIdToAdd: ids, includeUnavailable: true}, function () {
                if (callback) callback(id);
            });
        },
        remove: function (id, indices, callback) {
            return request('updatePlaylist', {playlistId: id, songIndexToRemove: indices, includeUnavailable: true}, callback);
        },
        reorder: function (id, indices, callback) {
            return request('updatePlaylist', {playlistId: id, songIndexOrder: indices}, callback, null, callback);
        },
        delete: function (id, callback) { return request('deletePlaylist', {id: id}, callback); },
        watchList: function (callback) {
            var previous, previousChanges = -1;
            watch(function (active) {
                return api.list(false, function (values) {
                    var snapshot = JSON.stringify(values);
                    if (active() && (snapshot !== previous || localChanges !== previousChanges)) {
                        previous = snapshot; previousChanges = localChanges; callback(values);
                    }
                });
            });
        },
        watchPlaylist: function (id, callback, deleted) {
            var previous, previousChanges = -1;
            watch(function (active) {
                return api.get(id, function (value, entries) {
                    var snapshot = JSON.stringify([value, entries]);
                    if (active() && (snapshot !== previous || localChanges !== previousChanges)) {
                        previous = snapshot; previousChanges = localChanges; callback(value, entries);
                    }
                },
                    function () { if (active()) deleted(id); });
            });
        }
    };
    return api;
}
