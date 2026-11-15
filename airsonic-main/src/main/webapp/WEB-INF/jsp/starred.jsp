<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <%@ include file="subsonicPlaylists.jsp" %>
    <%@ include file="subsonicStars.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>" defer></script>
    <script type="text/javascript">
        var starredDataUrl = "<c:url value='/rest/starredPage'/>";
        var starredConfig;

        function textCell(row, value, className) {
            var cell = document.createElement("td");
            if (className) cell.className = className;
            cell.textContent = value == null ? "" : value;
            row.appendChild(cell);
            return cell;
        }
        function actionCell(row, item, kind, data) {
            function action(action) {
                var cell = document.createElement("td"); cell.className = "fit"; row.appendChild(cell); action(cell);
            }
            var starred = kind !== "album";
            if (starred) {
                action(function(cell) {
                    var star = document.createElement("img");
                    star.src = item.starredDate ? starredConfig.starOn : starredConfig.starOff;
                    star.alt = "";
                    star.title = item.starredDate ? starredConfig.unstar : starredConfig.star;
                    star.style.cssText = "cursor:pointer;height:18px";
                    star.addEventListener("click", function() {
                        setMediaFilesStarred([item.id], !item.starredDate, function() {
                            item.starredDate = item.starredDate ? null : new Date().toISOString();
                            star.src = item.starredDate ? starredConfig.starOn : starredConfig.starOff;
                            star.title = item.starredDate ? starredConfig.unstar : starredConfig.star;
                        });
                    });
                    cell.appendChild(star);
                });
            }
            var canPlay = data.streamAllowed && !data.partyModeEnabled;
            if (kind === "video" && canPlay) {
                action(function(cell) {
                    if (data.webPlayer) {
                        var playLink = document.createElement("a");
                        playLink.href = "videoPlayer.view?id=" + encodeURIComponent(item.id);
                        playLink.target = "main";
                        appendIcon(playLink, starredConfig.playIcon, starredConfig.play);
                        cell.appendChild(playLink);
                    } else {
                        appendAction(cell, starredConfig.playIcon, starredConfig.play, function() { top.playQueue.onPlay(item.id); });
                    }
                });
            } else if (kind !== "video" && canPlay) {
                action(function(cell) { appendAction(cell, starredConfig.playIcon, starredConfig.play, function() { top.playQueue.onPlay(item.id); }); });
            }
            var canAdd = data.streamAllowed && !data.partyModeEnabled && kind !== "video";
            if (kind === "artist") canAdd = data.streamAllowed && (!data.partyModeEnabled || !item.directory);
            if (canAdd) {
                action(function(cell) { appendAction(cell, starredConfig.addIcon, starredConfig.addLast, function() {
                        top.playQueue.onAdd(item.id);
                        $().toastmessage("showSuccessToast", starredConfig.addLastToast);
                    });
                });
                action(function(cell) { appendAction(cell, starredConfig.addNextIcon, starredConfig.addNext, function() {
                        top.playQueue.onAddNext(item.id);
                        $().toastmessage("showSuccessToast", starredConfig.addNextToast);
                    });
                });
            }
        }
        function appendIcon(parent, src, title) {
            var icon = document.createElement("img");
            icon.src = src; icon.alt = title; icon.title = title;
            icon.style.cssText = "height:18px";
            parent.appendChild(icon);
        }
        function appendAction(parent, src, title, action) {
            var icon = document.createElement("img");
            icon.src = src; icon.alt = title; icon.title = title;
            icon.style.cssText = "cursor:pointer;height:18px";
            icon.addEventListener("click", action);
            parent.appendChild(icon);
        }
        function addArtist(item, data, body) {
            var row = document.createElement("tr");
            actionCell(row, item, "artist", data);
            var cell = textCell(row, "", "truncate");
            var link = document.createElement("a");
            link.href = "main.view?id=" + encodeURIComponent(item.id);
            link.textContent = item.name || "";
            cell.appendChild(link);
            body.appendChild(row);
        }
        function addSong(item, data, body) {
            var row = document.createElement("tr");
            actionCell(row, item, "song", data);
            textCell(row, item.title, "truncate");
            var albumCell = textCell(row, "", "truncate");
            var album = document.createElement("a");
            if (item.parentId != null) album.href = "main.view?id=" + encodeURIComponent(item.parentId);
            var albumLabel = document.createElement("span");
            albumLabel.className = "detail"; albumLabel.textContent = item.album || "";
            album.appendChild(albumLabel); albumCell.appendChild(album);
            var artist = document.createElement("span");
            artist.className = "detail"; artist.textContent = item.artist || "";
            textCell(row, "", "truncate").appendChild(artist);
            body.appendChild(row);
        }
        function addVideo(item, data, body) {
            var row = document.createElement("tr");
            actionCell(row, item, "video", data);
            var cell = textCell(row, "", "truncate");
            var link = document.createElement("a");
            link.href = "videoPlayer.view?id=" + encodeURIComponent(item.id);
            link.textContent = item.name || "";
            cell.appendChild(link);
            body.appendChild(row);
        }
        function addAlbum(item, data, container, index) {
            var card = document.createElement("div");
            card.className = "albumThumb";
            var imageLink = document.createElement("a");
            imageLink.href = "main.view?id=" + encodeURIComponent(item.id);
            imageLink.title = item.name || "";
            var image = document.createElement("img");
            image.src = "coverArt.view?id=" + encodeURIComponent(item.id) + "&size=" + encodeURIComponent(data.coverArtSize);
            image.alt = item.name || "";
            image.style.cssText = "width:" + data.coverArtSize + "px;height:" + data.coverArtSize + "px";
            imageLink.appendChild(image); card.appendChild(imageLink);
            var coverActions = document.createElement("div"); coverActions.className = "forward";
            appendAction(coverActions, starredConfig.playIcon, starredConfig.play, function() { top.playQueue.onPlay(item.id); });
            appendAction(coverActions, starredConfig.addIcon, starredConfig.addLast, function() { top.playQueue.onAdd(item.id); });
            card.appendChild(coverActions);
            var caption = document.createElement("div"); caption.className = "caption1";
            caption.style.width = (data.coverArtSize - 16) + "px";
            var title = document.createElement("a"); title.href = imageLink.href; title.textContent = item.name || starredConfig.unknown;
            caption.appendChild(title); card.appendChild(caption);
            var artist = document.createElement("div"); artist.className = "caption2";
            artist.style.width = (data.coverArtSize - 16) + "px"; artist.textContent = item.artist || "";
            card.appendChild(artist);
            container.appendChild(card);
        }
        function renderStarred(data) {
            var artists = data.artists || [], albums = data.albums || [], songs = data.songs || [], videos = data.videos || [];
            document.getElementById("loading").hidden = true;
            document.getElementById("empty").hidden = artists.length + albums.length + songs.length + videos.length > 0;
            var albumContainer = document.getElementById("albums");
            if (albums.length) {
                document.getElementById("albumHeading").hidden = false;
                albums.forEach(function(item, i) { addAlbum(item, data, albumContainer, i); });
            }
            var artistBody = document.getElementById("artistRows");
            artists.forEach(function(item) { addArtist(item, data, artistBody); });
            document.getElementById("artistHeading").hidden = artists.length === 0;
            document.getElementById("artistTable").hidden = artists.length === 0;
            var songBody = document.getElementById("songRows");
            songs.forEach(function(item) { addSong(item, data, songBody); });
            document.getElementById("songHeading").hidden = songs.length === 0;
            document.getElementById("songTable").hidden = songs.length === 0;
            document.getElementById("songActions").hidden = songs.length === 0;
            document.getElementById("videoRows").replaceChildren();
            videos.forEach(function(item) { addVideo(item, data, document.getElementById("videoRows")); });
            document.getElementById("videoHeading").hidden = videos.length === 0;
            document.getElementById("videoTable").hidden = videos.length === 0;
        }
        function onSavePlaylist() {
            playlistApi.create("starred", null, function(playlistId) {
                top.main.location.href = "playlist.view?id=" + encodeURIComponent(playlistId);
                $().toastmessage("showSuccessToast", starredConfig.savePlaylistToast);
            });
        }
        function onPlayAll() { top.playQueue.onPlayStarred(); }
        function init() {
            starredConfig = document.getElementById("starredConfig").dataset;
            fetch(starredDataUrl, {credentials: "same-origin", headers: {"Accept": "application/json"}})
                .then(function(response) { if (!response.ok) throw new Error("Request failed (" + response.status + ")"); return response.json(); })
                .then(renderStarred)
                .catch(function(error) {
                    document.getElementById("loading").hidden = true;
                    var target = document.getElementById("errors"); target.hidden = false;
                    target.textContent = starredConfig.loadError + ": " + error.message;
                });
        }
    </script>
</head>
<body class="mainframe bgcolor1" onload="init()">
<div id="starredConfig" hidden
     data-star-on="<spring:theme code='ratingOnImage'/>" data-star-off="<spring:theme code='ratingOffImage'/>"
     data-play-icon="<spring:theme code='playImage'/>" data-add-icon="<spring:theme code='addImage'/>"
     data-add-next-icon="<spring:theme code='addNextImage'/>" data-play="<fmt:message key='common.play'/>"
     data-star="<fmt:message key='playlist.more.star'/>" data-unstar="<fmt:message key='playlist.more.unstar'/>"
     data-add-last="<fmt:message key='main.addlast'/>" data-add-next="<fmt:message key='main.addnext'/>"
     data-add-last-toast="<fmt:message key='main.addlast.toast'/>" data-add-next-toast="<fmt:message key='main.addnext.toast'/>"
     data-save-playlist-toast="<fmt:message key='playlist.toast.saveasplaylist'/>" data-unknown="<fmt:message key='common.unknown'/>"
     data-load-error="<fmt:message key='starred.error'/>"></div>
<h1>
    <img src="<spring:theme code='starredImage'/>">
    <span style="vertical-align: middle"><fmt:message key="starred.title"/></span>
</h1>
<p id="loading"><em>Loading...</em></p>
<p class="warning" id="errors" hidden></p>
<p id="empty" style="padding-top:1em" hidden><em><fmt:message key="starred.empty"/></em></p>

<h2 id="albumHeading" hidden><fmt:message key="search.hits.albums"/></h2>
<div id="albums" style="padding-top:0.5em"></div>

<h2 id="artistHeading" hidden><fmt:message key="search.hits.artists"/></h2>
<table class="music indent" id="artistTable" hidden><tbody id="artistRows"></tbody></table>

<h2 id="songHeading" hidden><fmt:message key="search.hits.songs"/></h2>
<table class="music indent" id="songTable" hidden><tbody id="songRows"></tbody></table>
<div id="songActions" hidden>
    <div class="forward" style="float:left;padding-right:1.5em"><a href="#" onclick="onSavePlaylist();return false"><fmt:message key="playlist.save"/></a></div>
    <div class="forward" style="float:left"><a href="#" onclick="onPlayAll();return false"><fmt:message key="main.playall"/></a></div>
    <div style="clear:both"></div>
</div>

<h2 id="videoHeading" hidden><fmt:message key="search.hits.videos"/></h2>
<table class="music indent" id="videoTable" hidden><tbody id="videoRows"></tbody></table>
</body></html>
