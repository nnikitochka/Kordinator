/* The popup uses the same authenticated REST endpoint as external clients. */
function createSubsonicLyrics(options) {
    var currentRequest = 0;
    var controller;
    function visible(id, show) {
        document.getElementById(id).style.display = show ? "block" : "none";
    }
    return async function getLyrics(artist, title) {
        var request = ++currentRequest;
        if (controller) {
            controller.abort();
        }
        controller = new AbortController();
        visible("wait", true);
        ["lyrics", "noLyricsFound", "tryLater"].forEach(function (id) { visible(id, false); });
        document.getElementById("lyricsText").textContent = "";
        try {
            var headers = {};
            if (options.csrfHeader && options.csrfToken) {
                headers[options.csrfHeader] = options.csrfToken;
            }
            var response = await fetch(options.url, {
                method: "POST", credentials: "same-origin", headers: headers, signal: controller.signal,
                body: new URLSearchParams({f: "json", c: "airsonic-web", artist: artist, title: title,
                    includeLookupStatus: "true"})
            });
            if (!response.ok || response.redirected) {
                throw new Error("Lyrics request failed");
            }
            var body = (await response.json())["subsonic-response"];
            if (!body || body.status !== "ok" || !body.lyrics) {
                throw new Error("Subsonic lyrics request failed");
            }
            if (request !== currentRequest) {
                return;
            }
            var lyrics = body.lyrics;
            if (lyrics.tryLater) {
                visible("tryLater", true);
            } else if (lyrics.value != null && lyrics.value.trim() !== "") {
                document.getElementById("lyricsHeader").textContent =
                    (lyrics.artist || artist) + " - " + (lyrics.title || title);
                document.getElementById("lyricsText").textContent = lyrics.value;
                visible("lyrics", true);
            } else {
                visible("noLyricsFound", true);
            }
        } catch (error) {
            if (request === currentRequest) {
                visible("tryLater", true);
            }
        } finally {
            if (request === currentRequest) {
                visible("wait", false);
            }
        }
    };
}
