<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>

<html>
<head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <%@ include file="subsonicNowPlaying.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>
    <script type="text/javascript" src="<c:url value='/script/subsonic-scan-status.js'/>"></script>

    <script type="text/javascript">
        function scanningStatus(scanInfo) {
            $("#scanCount").text(scanInfo.count);
            if (scanInfo.scanning) $("#scanningStatus").show();
            else $("#scanningStatus").hide();
        }

        function safeLocalUrl(value) {
            if (!value) return null;
            try {
                var url = new URL(value, window.location.href);
                return url.origin === window.location.origin ? url.href : null;
            } catch (e) { return null; }
        }

        function addTextCell(row, text, className) {
            var cell = document.createElement('td');
            if (className) cell.className = className;
            cell.textContent = text == null ? '' : text;
            row.appendChild(cell);
            return cell;
        }

        function addStatus(status, table) {
            var body = document.getElementById(table + 'Table');
            var transferClass = 'playstatus-' + String(status.transferId || '').replace(/[^a-zA-Z0-9_-]/g, '');
            if (!transferClass || transferClass === 'playstatus-') return;
            document.querySelectorAll('.' + transferClass).forEach(function (row) { row.remove(); });

            var userRow = document.createElement('tr');
            userRow.className = transferClass;
            var userCell = document.createElement('td');
            userCell.colSpan = 2;
            userCell.className = 'detail';
            userCell.style.cssText = 'padding-top:1em;white-space:nowrap';
            var avatar = safeLocalUrl(status.avatarUrl);
            if (avatar) {
                var avatarImage = document.createElement('img');
                avatarImage.alt = 'Avatar'; avatarImage.src = avatar;
                avatarImage.style.cssText = 'padding-right:5pt;width:30px;height:30px';
                userCell.appendChild(avatarImage);
            }
            var username = document.createElement('b');
            username.textContent = status.username || '';
            userCell.appendChild(username);
            userRow.appendChild(userCell);
            body.appendChild(userRow);

            var detailRow = document.createElement('tr');
            detailRow.className = transferClass + ' mediafile-' + String(status.mediaFileId || '').replace(/[^a-zA-Z0-9_-]/g, '');
            var detailCell = document.createElement('td');
            detailCell.className = 'detail'; detailCell.style.paddingRight = '1em';
            var albumUrl = safeLocalUrl(status.albumUrl);
            var titleLink = document.createElement('a');
            titleLink.title = status.tooltip || '';
            titleLink.target = 'main';
            if (albumUrl) titleLink.href = albumUrl;
            if (status.artist != null) {
                titleLink.appendChild(document.createTextNode(status.artist));
                titleLink.appendChild(document.createElement('br'));
            }
            var title = document.createElement('span');
            title.className = 'songTitle'; title.textContent = status.title || '';
            titleLink.appendChild(title); detailCell.appendChild(titleLink);
            detailCell.appendChild(document.createElement('br'));
            var lyricsUrl = safeLocalUrl(status.lyricsUrl);
            if (lyricsUrl) {
                var lyricsWrap = document.createElement('span'); lyricsWrap.className = 'forward';
                var lyrics = document.createElement('a'); lyrics.href = lyricsUrl;
                lyrics.textContent = document.getElementById('lyricsLabel').textContent;
                lyrics.addEventListener('click', function () { popupSize(lyrics, 'lyrics', 500, 550); });
                lyricsWrap.appendChild(lyrics); detailCell.appendChild(lyricsWrap);
            }
            detailRow.appendChild(detailCell);

            var coverCell = document.createElement('td');
            var coverUrl = safeLocalUrl(status.coverArtUrl);
            if (coverUrl) {
                var coverLink = document.createElement('a');
                coverLink.title = status.tooltip || ''; coverLink.target = 'main';
                if (albumUrl) coverLink.href = albumUrl;
                var cover = document.createElement('img');
                cover.alt = 'Cover art'; cover.src = coverUrl;
                cover.className = 'dropshadow'; cover.width = 60; cover.height = 60;
                coverLink.appendChild(cover); coverCell.appendChild(coverLink);
            }
            detailRow.appendChild(coverCell); body.appendChild(detailRow);

            if (Number(status.minutesAgo) > 4) {
                var ageRow = document.createElement('tr'); ageRow.className = transferClass;
                var ageCell = addTextCell(ageRow, status.minutesAgo + ' ' + document.getElementById('minutesAgoLabel').textContent, 'detail');
                ageCell.colSpan = 2; body.appendChild(ageRow);
            }
            $('#' + table).show();
        }

        function showUpgrade(settings) {
            if (!settings.newVersionAvailable || !settings.latestVersion || !settings.latestVersionUrl) return;
            var url;
            try { url = new URL(settings.latestVersionUrl); } catch (e) { return; }
            if (url.protocol !== 'https:' || url.hostname !== 'github.com' || url.pathname.indexOf('/airsonic-advanced/airsonic-advanced/releases/tag/') !== 0) return;
            var warning = document.getElementById('upgradeTemplate').cloneNode(true);
            warning.removeAttribute('id'); warning.style.display = '';
            var walker = document.createTreeWalker(warning, NodeFilter.SHOW_TEXT);
            var node;
            while ((node = walker.nextNode())) {
                node.nodeValue = node.nodeValue.replace('__BRAND__', settings.brand || '')
                    .replace('__VERSION__', settings.latestVersion);
            }
            var link = warning.querySelector('a');
            if (link) { link.removeAttribute('onclick'); link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer'; }
            document.getElementById('upgrade').appendChild(warning);
        }

        function init() {
            watchSubsonicScanStatus({
                url: "<c:url value='/rest/getScanStatus'/>", csrfHeader: "${_csrf.headerName}",
                csrfToken: "${_csrf.token}"
            }, scanningStatus);

            fetch("<c:url value='/rest/rightPageSettings'/>", {credentials: 'same-origin', cache: 'no-store'})
                .then(function (response) {
                    if (!response.ok || response.redirected) throw new Error('Right panel settings request failed');
                    return response.json();
                }).then(function (settings) {
                    showUpgrade(settings);
                    if (!settings.showNowPlaying) return;
                    watchNowPlaying('right-now-playing', function (current, recent) {
                        [['nowPlaying', current], ['recentlyPlayed', recent]].forEach(function (group) {
                            $('#' + group[0] + 'Table').empty(); $('#' + group[0]).hide();
                            group[1].forEach(function (status) { addStatus(status, group[0]); });
                        });
                    });
                }).catch(function (error) { console.error(error); });
        }
    </script>
</head>
<body class="bgcolor1 rightframe" style="padding-top:2em" onload="init()">

<div id="upgrade"></div>
<div id="upgradeTemplate" class="warning" style="display:none;padding-bottom:1em">
    <fmt:message key="top.upgrade"><fmt:param value="__BRAND__"/><fmt:param value="__VERSION__"/><fmt:param value="#"/></fmt:message>
</div>
<span id="lyricsLabel" style="display:none"><fmt:message key="main.lyrics"/></span>
<span id="minutesAgoLabel" style="display:none"><fmt:message key="main.minutesago"/></span>

<div id="scanningStatus" style="display:none" class="warning">
    <img src="<spring:theme code='scanningImage'/>" title="" alt=""> <fmt:message key="main.scanning"/> <span id="scanCount"></span>
</div>

<div id="nowPlaying" style="display:none">
    <h2><fmt:message key="main.nowplaying"/></h2>
    <table id="nowPlayingTable" style="width:100%"></table>
</div>

<div id="recentlyPlayed" style="display:none">
    <h2><fmt:message key="main.recentlyplayed"/></h2>
    <table id="recentlyPlayedTable" style="width:100%"></table>
</div>

</body>
</html>
