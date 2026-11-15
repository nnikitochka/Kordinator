/* Keep click handling delegated because rating buttons are rendered after page load. */
function renderSubsonicRating(container, rating, mediaId, readOnly, config) {
    var doc = container.ownerDocument || document;
    var value = Number(rating) || 0;
    container.replaceChildren();

    function iconFor(star) {
        if (value >= star * 10) return config.ratingOnImage;
        if (value >= star * 10 - 7 && value <= star * 10 - 3) return config.ratingHalfImage;
        return config.ratingOffImage;
    }

    function appendIcon(parent, src, title) {
        var image = doc.createElement("img");
        image.src = src;
        image.alt = "";
        image.title = title;
        image.style.cssText = "margin-right:-3px;height:18px";
        parent.appendChild(image);
    }

    for (var star = 1; star <= 5; star++) {
        var title = config.ratingLabel + " " + (readOnly ? value / 10 : star);
        if (readOnly) {
            var image = doc.createElement("img");
            image.src = iconFor(star);
            image.alt = "";
            image.title = title;
            image.style.cssText = "margin-right:-3px;height:18px";
            container.appendChild(image);
        } else {
            var button = doc.createElement("button");
            button.type = "button";
            button.dataset.mediaId = String(mediaId);
            button.dataset.mediaRating = String(star);
            button.style.cssText = "border:0;padding:0;background:transparent;cursor:pointer";
            appendIcon(button, iconFor(star), title);
            container.appendChild(button);
        }
    }

    if (!readOnly) {
        container.appendChild(doc.createTextNode("\u00a0| "));
        var clearButton = doc.createElement("button");
        clearButton.type = "button";
        clearButton.dataset.mediaId = String(mediaId);
        clearButton.dataset.mediaRating = "0";
        clearButton.style.cssText = "border:0;padding:0;background:transparent;cursor:pointer";
        appendIcon(clearButton, config.clearRatingImage, config.clearRatingLabel);
        container.appendChild(clearButton);
    }
}

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
