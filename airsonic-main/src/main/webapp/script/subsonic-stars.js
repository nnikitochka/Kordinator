/* Shared by the built-in pages; uses the existing authenticated browser session. */
function createSubsonicStars(options) {
    var pending = Promise.resolve();
    return function setStarred(ids, starred, onSuccess) {
        if (!ids.length) {
            return pending;
        }
        // Preserve the order of writes, including overlapping bulk selections.
        pending = pending.then(function () {
            var form = new URLSearchParams({f: "json", c: "airsonic-web"});
            ids.forEach(function (id) { form.append("id", id); });
            var headers = {};
            if (options.csrfHeader && options.csrfToken) {
                headers[options.csrfHeader] = options.csrfToken;
            }
            return fetch(starred ? options.starUrl : options.unstarUrl, {
                method: "POST",
                credentials: "same-origin",
                headers: headers,
                body: form
            }).then(function (response) {
                if (!response.ok || response.redirected) {
                    throw new Error("Star request failed");
                }
                return response.json();
            }).then(function (body) {
                var response = body["subsonic-response"];
                if (!response || response.status !== "ok") {
                    throw new Error("Subsonic star request failed");
                }
                if (onSuccess) {
                    onSuccess();
                }
            });
        }).catch(function () {
            options.onError();
        });
        return pending;
    };
}
