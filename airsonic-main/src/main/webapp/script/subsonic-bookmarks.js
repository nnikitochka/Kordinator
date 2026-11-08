/* Browser-session client for the shared Subsonic bookmark API. */
function createSubsonicBookmarks(options) {
    var pending = Promise.resolve();
    var localChanges = 0;
    function request(url, parameters, onSuccess) {
        // Keep automatic progress writes and polling reads in order.
        pending = pending.then(async function () {
            var form = new URLSearchParams(parameters);
            form.set("f", "json");
            form.set("c", "airsonic-web");
            var headers = {};
            if (options.csrfHeader && options.csrfToken) {
                headers[options.csrfHeader] = options.csrfToken;
            }
            var response = await fetch(url, {
                method: "POST", credentials: "same-origin", headers: headers, body: form
            });
            if (!response.ok || response.redirected) {
                throw new Error("Bookmark request failed");
            }
            var body = (await response.json())["subsonic-response"];
            if (!body || body.status !== "ok") {
                throw new Error("Subsonic bookmark request failed");
            }
            if (url !== options.listUrl) localChanges++;
            if (onSuccess) {
                onSuccess(body);
            }
        }).catch(function () { options.onError(); });
        return pending;
    }
    var api = {
        list: function (onSuccess) {
            return request(options.listUrl, {includeEntryAvailability: "true"}, function (body) {
                var bookmarks = {};
                ((body.bookmarks || {}).bookmark || []).forEach(function (bookmark) {
                    var entry = bookmark.entry;
                    if (!entry) {
                        return;
                    }
                    bookmarks[entry.id] = {
                        id: entry.id,
                        mediaFileEntry: Object.assign({title: null, album: null, artist: null, duration: null}, entry, {
                            starred: Boolean(entry.starred), present: bookmark.entryPresent === true
                        }),
                        positionMillis: bookmark.position == null ? null : bookmark.position,
                        comment: bookmark.comment == null ? null : bookmark.comment,
                        created: bookmark.created == null ? null : bookmark.created,
                        changed: bookmark.changed == null ? null : bookmark.changed
                    };
                });
                onSuccess(bookmarks);
            });
        },
        watch: function (onSuccess) {
            var disposed = false, timer, previous, previousChanges = -1;
            function poll() {
                if (disposed) return;
                api.list(function (bookmarks) {
                    var snapshot = JSON.stringify(bookmarks);
                    if (!disposed && (snapshot !== previous || localChanges !== previousChanges)) {
                        previous = snapshot;
                        previousChanges = localChanges;
                        onSuccess(bookmarks);
                    }
                }).then(function () {
                    if (!disposed) timer = window.setTimeout(poll, 20000);
                });
            }
            poll();
            window.addEventListener("pagehide", function () {
                disposed = true;
                window.clearTimeout(timer);
            }, {once: true});
        },
        create: function (id, position, comment, onSuccess) {
            return request(options.createUrl, {id: id, position: position, comment: comment}, onSuccess);
        },
        remove: function (id, onSuccess) {
            return request(options.deleteUrl, {id: id}, onSuccess);
        }
    };
    return api;
}
