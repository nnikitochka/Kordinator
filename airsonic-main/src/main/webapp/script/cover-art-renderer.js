(function (root, factory) {
    if (typeof module === "object" && module.exports) {
        module.exports = factory;
    } else {
        root.createCoverArtRenderer = factory;
    }
}(typeof window === "undefined" ? globalThis : window, function createCoverArtRenderer(config, documentRef) {
    "use strict";
    var root = typeof window === "undefined" ? globalThis : window;
    var doc = documentRef || document;

    function withQuery(path, parameters) {
        var query = Object.keys(parameters).filter(function (key) {
            return parameters[key] !== undefined && parameters[key] !== null;
        }).map(function (key) {
            return encodeURIComponent(key) + "=" + encodeURIComponent(parameters[key]);
        }).join("&");
        return path + (path.indexOf("?") < 0 ? "?" : "&") + query;
    }

    function element(tag, className, text) {
        var node = doc.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function render(options) {
        var playlist = options.playlistId !== undefined && options.playlistId !== null;
        var id = playlist ? options.playlistId : options.albumId;
        if (id === undefined || id === null) throw new Error("Cover art requires an album or playlist id");

        var size = options.size === undefined ? "auto" : options.size;
        var title = options.caption1 || "";
        var card = element("div", "coverart dropshadow");
        var frame = element("div");
        frame.style.width = size === "auto" ? "auto" : size + "px";
        frame.style.maxWidth = frame.style.width;
        frame.style.height = frame.style.width;
        frame.style.maxHeight = frame.style.width;
        frame.style.cursor = "pointer";
        if (options.hideOverflow) frame.style.overflow = "hidden";
        frame.title = title;

        var target = playlist ? config.playlistPageUrl : config.albumPageUrl;
        var targetUrl = withQuery(target, {id: id});
        var artId = playlist ? "pl-" + id : id;
        var imageUrl = withQuery(config.coverArtUrl, {size: size === "auto" ? undefined : size, id: artId});
        var imageLink;
        if (options.showLink) {
            imageLink = element("a");
            imageLink.href = targetUrl;
            imageLink.title = title;
        } else if (options.showZoom && !playlist) {
            imageLink = element("a");
            imageLink.href = withQuery(config.coverArtUrl, {id: id});
            imageLink.className = "fancy";
            imageLink.rel = "zoom";
            imageLink.title = title;
        }

        var image = element("img");
        image.src = imageUrl;
        image.alt = title;
        image.style.display = "none";
        if (imageLink) {
            imageLink.appendChild(image);
            frame.appendChild(imageLink);
            if (imageLink.className === "fancy" && root.jQuery && root.jQuery.fn && root.jQuery.fn.fancyZoom) {
                root.jQuery(imageLink).fancyZoom({minBorder: 30});
            }
        } else {
            frame.appendChild(image);
        }

        var play = element("img");
        play.src = config.playOverlayUrl;
        play.alt = "Play";
        play.style.cssText = "position:relative;top:8px;left:8px;z-index:2;display:none";
        var playWrap = element("div");
        playWrap.style.cssText = "position:relative;width:0;height:0";
        playWrap.appendChild(play);
        frame.insertBefore(playWrap, frame.firstChild);

        var add = element("img");
        add.src = config.addOverlayUrl;
        add.alt = "Add";
        add.style.cssText = "position:relative;top:8px;left:48px;z-index:2;display:none";
        var addWrap = element("div");
        addWrap.style.cssText = "position:relative;width:0;height:0";
        addWrap.appendChild(add);
        frame.insertBefore(addWrap, playWrap.nextSibling);

        function queueAction(kind) {
            var queue = root.top && root.top.playQueue;
            if (!queue) return;
            var method = playlist ? "on" + kind + "Playlist" : "on" + kind;
            if (typeof queue[method] === "function") queue[method](id);
        }
        play.addEventListener("click", function (event) { event.preventDefault(); event.stopPropagation(); queueAction("Play"); });
        add.addEventListener("click", function (event) { event.preventDefault(); event.stopPropagation(); queueAction("Add"); });
        frame.addEventListener("mouseenter", function () {
            play.style.display = "";
            add.style.display = "";
            if (root.jQuery) root.jQuery(image).stop().animate({opacity: 0.7}, 150);
        });
        frame.addEventListener("mouseleave", function () {
            play.style.display = "none";
            add.style.display = "none";
            if (root.jQuery) root.jQuery(image).stop().animate({opacity: 1}, 150);
        });
        card.appendChild(frame);

        var captionCount = Number(options.captionCount || 0);
        var captionWidth = Math.max(0, Number(size) - 16) + "px";
        if (captionCount > 0) {
            var caption1 = element("div", "caption1");
            caption1.style.width = captionWidth;
            var captionLink = element("a", "", title);
            captionLink.href = targetUrl;
            captionLink.title = title;
            caption1.appendChild(captionLink);
            card.appendChild(caption1);
        }
        if (captionCount > 1) {
            var caption2 = element("div", "caption2", options.caption2 || "");
            caption2.style.width = captionWidth;
            caption2.appendChild(doc.createTextNode("\u00a0"));
            card.appendChild(caption2);
        }
        if (captionCount > 2) {
            var caption3 = element("div", "caption3", options.caption3 || "");
            caption3.style.width = captionWidth;
            caption3.appendChild(doc.createTextNode("\u00a0"));
            card.appendChild(caption3);
        }

        if (options.showChange || options.showZoom) {
            var links = element("div");
            links.style.cssText = "padding-top:6px;text-align:right";
            if (options.showChange && !playlist) {
                var change = element("a", "detail", config.changeLabel);
                change.href = withQuery(config.changeCoverArtUrl, {id: id});
                links.appendChild(change);
            }
            if (options.showChange && options.showZoom && !playlist) links.appendChild(doc.createTextNode(" | "));
            if (options.showZoom && !playlist) {
                var zoom = element("a", "detail", config.zoomLabel);
                zoom.href = withQuery(config.coverArtUrl, {id: id});
                zoom.rel = "zoom";
                zoom.title = title;
                links.appendChild(zoom);
            }
            if (links.childNodes.length) card.appendChild(links);
        }

        var delay = Number(options.appearAfter || 0);
        root.setTimeout(function () {
            if (root.jQuery) root.jQuery(image).fadeIn(500);
            else image.style.display = "";
        }, delay);
        return card;
    }

    return {render: render, withQuery: withQuery, config: config};
}));
