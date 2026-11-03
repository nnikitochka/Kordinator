/* Delegation also covers rating fragments loaded after the page has initialized. */
function installSubsonicRating(options) {
    var pending = false;
    document.addEventListener("click", async function (event) {
        var button = event.target.closest("button[data-media-rating]");
        if (!button || button.disabled || pending) {
            return;
        }
        event.preventDefault();
        pending = true;
        var controls = document.querySelectorAll("button[data-media-rating]");
        controls.forEach(function (control) { control.disabled = true; });
        var id = button.dataset.mediaId;
        try {
            var form = new URLSearchParams({
                f: "json", c: "airsonic-web", id: id, rating: button.dataset.mediaRating
            });
            var headers = {};
            if (options.csrfHeader && options.csrfToken) {
                headers[options.csrfHeader] = options.csrfToken;
            }
            var response = await fetch(options.url, {
                method: "POST", credentials: "same-origin", headers: headers, body: form
            });
            if (!response.ok || response.redirected) {
                throw new Error("Rating request failed");
            }
            var body = await response.json();
            if (!body["subsonic-response"] || body["subsonic-response"].status !== "ok") {
                throw new Error("Subsonic rating request failed");
            }
            options.onSuccess(id);
        } catch (error) {
            options.onError();
        } finally {
            pending = false;
            controls.forEach(function (control) { control.disabled = false; });
        }
    });
}
