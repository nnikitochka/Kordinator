<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript">
        var searchIcons = {
            play: "<spring:theme code='playImage'/>",
            add: "<spring:theme code='addImage'/>",
            addNext: "<spring:theme code='addNextImage'/>"
        };
        var searchMessages = {
            noHits: "<fmt:message key='search.hits.none'/>",
            artists: "<fmt:message key='search.hits.artists'/>",
            albums: "<fmt:message key='search.hits.albums'/>",
            songs: "<fmt:message key='search.hits.songs'/>",
            more: "<fmt:message key='search.hits.more'/>",
            play: "<fmt:message key='common.play'/>",
            add: "<fmt:message key='main.addlast'/>",
            addNext: "<fmt:message key='main.addnext'/>"
        };

        function showMore(className, button) {
            document.querySelectorAll("." + className).forEach(function (row) { row.hidden = false; });
            button.hidden = true;
        }

        function image(src, title) {
            var result = document.createElement("img");
            result.src = src;
            result.alt = title;
            result.title = title;
            result.style.cssText = "cursor:pointer;height:18px";
            return result;
        }

        function mainLink(ids, text) {
            var link = document.createElement("a");
            var params = new URLSearchParams();
            ids.forEach(function (id) { params.append("id", id); });
            link.href = "main.view?" + params.toString();
            link.textContent = text || "";
            return link;
        }

        function actionCells(row, id, video, directory) {
            var playCell = row.insertCell();
            var addCell = row.insertCell();
            var addNextCell = row.insertCell();
            [playCell, addCell, addNextCell].forEach(function (cell) { cell.className = "fit"; });
            if (!searchResults.streamAllowed) return;
            if (!searchResults.partyMode) {
                if (video) {
                    var videoLink = document.createElement("a");
                    videoLink.href = "videoPlayer.view?id=" + encodeURIComponent(id);
                    videoLink.appendChild(image(searchIcons.play, searchMessages.play));
                    playCell.appendChild(videoLink);
                } else {
                    var play = image(searchIcons.play, searchMessages.play);
                    play.onclick = function () { top.playQueue.onPlay(id); };
                    playCell.appendChild(play);
                }
            }
            if (!video && (!searchResults.partyMode || !directory)) {
                var add = image(searchIcons.add, searchMessages.add);
                add.onclick = function () { top.playQueue.onAdd(id); };
                addCell.appendChild(add);
                var addNext = image(searchIcons.addNext, searchMessages.addNext);
                addNext.onclick = function () { top.playQueue.onAddNext(id); };
                addNextCell.appendChild(addNext);
            }
        }

        function makeTableSection(title, className, entries, limit, renderRow) {
            if (!entries || entries.length === 0) return;
            var heading = document.createElement("h2");
            heading.textContent = title;
            document.getElementById("searchResults").appendChild(heading);
            var table = document.createElement("table");
            table.className = "music indent";
            entries.forEach(function (entry, index) {
                var row = table.insertRow();
                row.className = className;
                row.hidden = index >= limit;
                renderRow(row, entry);
            });
            document.getElementById("searchResults").appendChild(table);
            if (entries.length > limit) {
                var more = document.createElement("div");
                more.className = "forward";
                var button = document.createElement("a");
                button.href = "#";
                button.textContent = searchMessages.more;
                button.onclick = function (event) { event.preventDefault(); showMore(className, more); };
                more.appendChild(button);
                document.getElementById("searchResults").appendChild(more);
            }
        }

        var searchResults;
        function renderSearchResults(results) {
            searchResults = results;
            var root = document.getElementById("searchResults");
            root.replaceChildren();
            var hasHits = results.artists.length + results.albums.length + results.songs.length > 0;
            if (!hasHits) {
                var empty = document.createElement("p");
                empty.className = "warning";
                empty.textContent = searchMessages.noHits;
                root.appendChild(empty);
                return;
            }

            makeTableSection(searchMessages.artists, "artistRow", results.artists, 5, function (row, item) {
                var ids = item.ids || [];
                actionCells(row, ids[0], false, true);
                var name = row.insertCell();
                name.className = "truncate";
                name.appendChild(mainLink(ids, item.name));
            });
            makeTableSection(searchMessages.albums, "albumRow", results.albums, 5, function (row, item) {
                var ids = item.ids || [];
                actionCells(row, ids[0], false, true);
                var name = row.insertCell();
                name.className = "truncate";
                name.appendChild(mainLink(ids, item.name));
                var artist = row.insertCell();
                artist.className = "truncate detail";
                artist.textContent = item.artist || "";
            });
            makeTableSection(searchMessages.songs, "songRow", results.songs, 15, function (row, item) {
                actionCells(row, item.id, item.video, false);
                var title = row.insertCell();
                title.className = "truncate";
                title.textContent = item.title || "";
                var album = row.insertCell();
                album.className = "truncate";
                var params = new URLSearchParams({path: item.folderId + ":" + item.parentPath});
                var albumLink = document.createElement("a");
                albumLink.href = "main.view?" + params.toString();
                var albumLabel = document.createElement("span");
                albumLabel.className = "detail";
                albumLabel.textContent = item.albumName || "";
                albumLink.appendChild(albumLabel);
                album.appendChild(albumLink);
                var artist = row.insertCell();
                artist.className = "truncate detail";
                artist.textContent = item.artist || "";
            });
        }

        function loadSearchResults() {
            var query = document.getElementById("initialQuery").value;
            fetch("<c:url value='/rest/searchPage'/>?query=" + encodeURIComponent(query), {credentials: "same-origin"})
                .then(function (response) {
                    if (!response.ok) throw new Error("Search request failed");
                    return response.json();
                })
                .then(renderSearchResults)
                .catch(function (error) { console.error(error); });
        }
    </script>
</head>
<body class="mainframe bgcolor1" onload="loadSearchResults()">

<h1>
    <img src="<spring:theme code='searchImage'/>" alt=""/>
    <span style="vertical-align: middle"><fmt:message key="search.title"/></span>
</h1>

<input type="hidden" id="initialQuery" value="<c:out value='${requestScope.initialQuery}'/>"/>
<div id="searchResults"></div>

</body></html>
