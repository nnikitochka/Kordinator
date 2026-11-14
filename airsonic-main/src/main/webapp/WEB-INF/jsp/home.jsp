<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript">
        var homeIcons = {
            home: "<spring:theme code='homeImage'/>",
            back: "<spring:theme code='backImage'/>",
            forward: "<spring:theme code='forwardImage'/>",
            refresh: "<spring:theme code='refreshImage'/>",
            shuffle: "<spring:theme code='shuffleImage'/>",
            play: "<spring:theme code='playOverlayImage'/>",
            add: "<spring:theme code='addOverlayImage'/>",
            ratingOn: "<spring:theme code='ratingOnImage'/>",
            ratingHalf: "<spring:theme code='ratingHalfImage'/>",
            ratingOff: "<spring:theme code='ratingOffImage'/>",
            coverArt: "<c:url value='/rest/getCoverArt'/>",
            api: "<c:url value='/rest/homePage'/>",
            homePage: "<c:url value='/home.view'/>",
            mainPage: "<c:url value='/main.view'/>",
            unknown: "<fmt:message key='common.unknown'/>",
            playTitle: "<fmt:message key='common.play'/>",
            addTitle: "<fmt:message key='main.addlast'/>",
            refreshTitle: "<fmt:message key='common.refresh'/>",
            shuffleTitle: "<fmt:message key='home.shuffle'/>",
            albumsLabel: "<fmt:message key='home.albums'/>",
            decadeLabel: "<fmt:message key='home.decade.text'/>",
            genreLabel: "<fmt:message key='home.genre.text'/>",
            decadeTitle: "<fmt:message key='home.decade.title'/>",
            genreTitle: "<fmt:message key='home.genre.title'/>",
            scanMessage: "<fmt:message key='home.scan'/>",
            missingMessage: "<fmt:message key='top.missing'/>",
            playCount: "<fmt:message key='home.playcount'/>",
            lastPlayed: "<fmt:message key='home.lastplayed'/>",
            created: "<fmt:message key='home.created'/>",
            detailColor: "<spring:theme code='detailColor'/>"
        };

        var homeCategoryIds = ["random", "newest", "starred", "highest", "frequent", "recent", "decade", "genre", "alphabetical"];
        var homeCategoryLabels = [
            "<fmt:message key='home.random.title'/>", "<fmt:message key='home.newest.title'/>",
            "<fmt:message key='home.starred.title'/>", "<fmt:message key='home.highest.title'/>",
            "<fmt:message key='home.frequent.title'/>", "<fmt:message key='home.recent.title'/>",
            "<fmt:message key='home.decade.title'/>", "<fmt:message key='home.genre.title'/>",
            "<fmt:message key='home.alphabetical.title'/>"
        ];

        function makeImage(src, alt) {
            var image = document.createElement("img");
            image.src = src;
            image.alt = alt || "";
            return image;
        }

        function queryFor(model) {
            var params = new URLSearchParams({listType: model.listType, listOffset: model.listOffset});
            if (model.genre) params.set("genre", model.genre);
            if (model.decade) params.set("decade", model.decade);
            return params;
        }

        function pageUrl(model, offset) {
            var params = queryFor(model);
            params.set("listOffset", offset);
            return homeIcons.homePage + "?" + params.toString();
        }

        function pager(model) {
            var table = document.createElement("table");
            var row = table.insertRow();
            if (model.musicFolder) {
                var folderCell = row.insertCell();
                folderCell.style.paddingRight = "2em";
                var folder = document.createElement("div");
                folder.style.cssText = "border:1px solid #" + homeIcons.detailColor + ";padding:0 0.5em";
                folder.textContent = model.musicFolder.name;
                folderCell.appendChild(folder);
            }

            if (model.listType !== "random") {
                if (model.albums.length > 0) {
                    var range = row.insertCell();
                    range.style.paddingRight = "0.5em";
                    range.textContent = homeIcons.albumsLabel.replace("{0}", model.listOffset + 1).replace("{1}", model.listOffset + model.albums.length);
                    if (model.listOffset > 0) {
                        var previous = row.insertCell();
                        var previousLink = document.createElement("a");
                        previousLink.href = pageUrl(model, model.listOffset - model.listSize);
                        previousLink.appendChild(makeImage(homeIcons.back));
                        previous.appendChild(previousLink);
                    }
                    if (model.albums.length === model.listSize) {
                        var next = row.insertCell();
                        var nextLink = document.createElement("a");
                        nextLink.href = pageUrl(model, model.listOffset + model.listSize);
                        nextLink.appendChild(makeImage(homeIcons.forward));
                        next.appendChild(nextLink);
                    }
                }

                if (model.listType === "decade" && model.decades.length) {
                    var decadeLabel = row.insertCell();
                    decadeLabel.textContent = homeIcons.decadeLabel;
                    var decadeCell = row.insertCell();
                    var decadeSelect = document.createElement("select");
                    model.decades.forEach(function (decade) {
                        var option = document.createElement("option");
                        option.value = decade;
                        option.textContent = decade;
                        option.selected = decade === model.decade;
                        decadeSelect.appendChild(option);
                    });
                    decadeSelect.onchange = function () {
                        window.location.href = homeIcons.homePage + "?listType=decade&decade=" + encodeURIComponent(decadeSelect.value);
                    };
                    decadeCell.appendChild(decadeSelect);
                }
                if (model.listType === "genre" && model.genres.length) {
                    var genreLabel = row.insertCell();
                    genreLabel.textContent = homeIcons.genreLabel;
                    var genreCell = row.insertCell();
                    var genreSelect = document.createElement("select");
                    model.genres.forEach(function (genre) {
                        var option = document.createElement("option");
                        option.value = genre.name;
                        option.textContent = genre.name + " (" + genre.albumCount + ")";
                        option.selected = genre.name === model.genre;
                        genreSelect.appendChild(option);
                    });
                    genreSelect.onchange = function () {
                        window.location.href = homeIcons.homePage + "?listType=genre&genre=" + encodeURIComponent(genreSelect.value);
                    };
                    genreCell.appendChild(genreSelect);
                }
            }

            var refreshCell = row.insertCell();
            refreshCell.style.paddingRight = "2em";
            var refresh = document.createElement("a");
            refresh.href = "javascript:top.main.location.reload()";
            refresh.appendChild(makeImage(homeIcons.refresh, "Refresh"));
            refresh.appendChild(document.createTextNode(" " + homeIcons.refreshTitle));
            refreshCell.appendChild(refresh);

            if (model.albums.length > 0) {
                var shuffleCell = row.insertCell();
                var shuffle = document.createElement("a");
                shuffle.href = "#";
                shuffle.appendChild(makeImage(homeIcons.shuffle, "Shuffle"));
                shuffle.appendChild(document.createTextNode(" " + homeIcons.shuffleTitle));
                shuffle.onclick = function (event) {
                    event.preventDefault();
                    top.playQueue.onPlayShuffle(model.listType, model.listOffset, model.listSize, model.genre, model.decade);
                };
                shuffleCell.appendChild(shuffle);
            }
            return table;
        }

        function addRating(container, rating) {
            if (rating == null) return;
            for (var star = 1; star <= 5; star++) {
                var value = rating / 10;
                var icon = value >= star ? homeIcons.ratingOn : (value >= star - 0.7 && value <= star - 0.3 ? homeIcons.ratingHalf : homeIcons.ratingOff);
                var image = makeImage(icon);
                image.style.cssText = "height:18px;margin-right:-3px";
                image.title = "" + (rating / 10);
                container.appendChild(image);
            }
        }

        function renderAlbum(album, index, model) {
            var thumb = document.createElement("div");
            thumb.className = "albumThumb";
            var cover = document.createElement("div");
            cover.className = "coverart dropshadow";
            var coverBox = document.createElement("div");
            coverBox.style.cssText = "width:" + model.coverArtSize + "px;max-width:" + model.coverArtSize + "px;height:" + model.coverArtSize + "px;max-height:" + model.coverArtSize + "px;cursor:pointer;overflow:hidden";
            coverBox.title = album.albumTitle || "";
            var params = new URLSearchParams({size: model.coverArtSize, id: album.id});
            var img = makeImage(homeIcons.coverArt + "?" + params.toString(), album.albumTitle);
            img.style.display = "none";
            img.onload = function () { $(img).fadeIn(500); };
            var play = makeImage(homeIcons.play, "Play");
            play.style.cssText = "position:absolute;top:8px;left:8px;z-index:2;display:none";
            var add = makeImage(homeIcons.add, "Add");
            add.style.cssText = "position:absolute;top:8px;left:48px;z-index:2;display:none";
            var overlay = document.createElement("div");
            overlay.style.cssText = "position:relative;width:0;height:0";
            overlay.appendChild(play);
            overlay.appendChild(add);
            coverBox.appendChild(overlay);
            var link = document.createElement("a");
            link.href = homeIcons.mainPage + "?id=" + encodeURIComponent(album.id);
            link.title = album.albumTitle || "";
            link.appendChild(img);
            coverBox.appendChild(link);
            coverBox.onmouseenter = function () { play.style.display = "block"; add.style.display = "block"; $(img).stop().animate({opacity: 0.7}, 150); };
            coverBox.onmouseleave = function () { play.style.display = "none"; add.style.display = "none"; $(img).stop().animate({opacity: 1.0}, 150); };
            play.onclick = function (event) { event.preventDefault(); event.stopPropagation(); top.playQueue.onPlay(album.id); };
            add.onclick = function (event) { event.preventDefault(); event.stopPropagation(); top.playQueue.onAdd(album.id); };
            cover.appendChild(coverBox);

            var caption = document.createElement("div");
            caption.className = "caption1";
            caption.style.width = (model.coverArtSize - 16) + "px";
            var captionLink = document.createElement("a");
            captionLink.href = link.href;
            captionLink.title = album.albumTitle || "";
            captionLink.textContent = album.albumTitle || homeIcons.unknown;
            caption.appendChild(captionLink);
            cover.appendChild(caption);
            var artist = document.createElement("div");
            artist.className = "caption2";
            artist.style.width = (model.coverArtSize - 16) + "px";
            artist.textContent = (album.artist || "") + "\u00a0";
            cover.appendChild(artist);

            var detail = album.playCount != null ? homeIcons.playCount.replace("{0}", album.playCount) : "";
            if (album.lastPlayed) detail = homeIcons.lastPlayed.replace("{0}", new Date(album.lastPlayed).toLocaleDateString());
            if (album.created) detail = homeIcons.created.replace("{0}", new Date(album.created).toLocaleDateString());
            if (album.year != null) detail = "" + album.year;
            if (detail) {
                var caption3 = document.createElement("div");
                caption3.className = "caption3";
                caption3.style.width = (model.coverArtSize - 16) + "px";
                caption3.textContent = detail + "\u00a0";
                cover.appendChild(caption3);
            }
            addRating(cover, album.rating);
            thumb.appendChild(cover);
            thumb.style.animationDelay = (index * 30) + "ms";
            return thumb;
        }

        function renderHome(model) {
            var root = document.getElementById("homeContent");
            root.replaceChildren();
            if (model.welcomeTitle) {
                var title = document.createElement("h1");
                title.appendChild(makeImage(homeIcons.home));
                var titleText = document.createElement("span");
                titleText.style.verticalAlign = "middle";
                titleText.textContent = model.welcomeTitle;
                title.appendChild(titleText);
                root.appendChild(title);
            }
            if (model.welcomeSubtitle) {
                var subtitle = document.createElement("h2");
                subtitle.textContent = model.welcomeSubtitle;
                root.appendChild(subtitle);
            }

            var categories = document.createElement("p");
            homeCategoryIds.forEach(function (id, index) {
                if (index) categories.appendChild(document.createTextNode("\u00a0|\u00a0"));
                var span = document.createElement("span");
                span.className = id === model.listType ? "headerSelected" : "header";
                if (id === model.listType) span.textContent = homeCategoryLabels[index];
                else {
                    var link = document.createElement("a");
                    link.href = homeIcons.homePage + "?listType=" + encodeURIComponent(id);
                    link.textContent = homeCategoryLabels[index];
                    span.appendChild(link);
                }
                categories.appendChild(span);
            });
            root.appendChild(categories);
            root.appendChild(pager(model));

            if (model.welcomeMessage) {
                var welcome = document.createElement("div");
                welcome.style.cssText = "width:15em;float:right;padding:0 1em;border-left:1px solid #" + homeIcons.detailColor;
                welcome.innerHTML = model.welcomeMessage;
                root.appendChild(welcome);
            }
            model.albums.forEach(function (album, index) { root.appendChild(renderAlbum(album, index, model)); });
            if (model.albums.length === model.listSize) root.appendChild(pager(model));
            if (!model.musicFoldersExist) $().toastmessage("showNoticeToast", homeIcons.missingMessage);
            if (model.isIndexBeingCreated) $().toastmessage("showNoticeToast", homeIcons.scanMessage);
        }

        function initHome() {
            fetch(homeIcons.api + window.location.search, {credentials: "same-origin"})
                .then(function (response) { if (!response.ok) throw new Error("Home request failed"); return response.json(); })
                .then(renderHome)
                .catch(function (error) { console.error(error); });
        }
    </script>
</head>
<body class="mainframe bgcolor1" onload="initHome()">
<div id="homeContent"></div>
</body></html>
