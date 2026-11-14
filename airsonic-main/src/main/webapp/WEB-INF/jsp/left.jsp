<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>
<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <%@ include file="subsonicPlaylists.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>
    <script type="text/javascript" src="<c:url value='/script/left-page.js?v=1'/>"></script>
    <script type="text/javascript" language="javascript">
        function init() {
            playlistApi.watchList(populatePlaylistCallback);
            loadLeftPage(document.getElementById('leftPageData'));
        }

        function createEmptyPlaylist() {
            showAllPlaylists();
            playlistApi.create("empty", null, function () { playlistApi.list(false, populatePlaylistCallback); });
        }

        function showAllPlaylists() {
            $('#playlistOverflow').show('blind');
            $('#showAllPlaylists').hide('blind');
        }

        function populatePlaylistCallback(playlists) {
            $("#playlists").empty();
            $("#playlistOverflow").empty();
            for (var i = 0; i < playlists.length; i++) {
                var playlist = playlists[i];
                var playlistName = escapeHtml(playlist.name) + "&nbsp;(" + playlist.fileCount + ")";
                var node = $("<p class='dense truncate playlist' id='playlistid-" + playlist.id + "' title='" + playlistName + "'><a target='main' href='playlist.view?id=" +
                        playlist.id + "'>" + playlistName + "</a></p>");
                var overflow = i > 9;
                if (!overflow) {
                    node.addClass("nonoverflown");
                }

                //append only if not already there
                if ($("#playlistid-" + playlist.id).length == 0) {
                    node.appendTo(overflow ? "#playlistOverflow" : "#playlists");
                }
            }

            if (playlists.length > 10 && !$('#playlistOverflow').is(":visible")) {
                $('#showAllPlaylists').show();
            }
        }

    </script>
</head>

<body class="bgcolor2 leftframe" onload="init()">
<a name="top"></a>

<div id="leftPageData" data-url="<c:url value='/rest/leftPageData'/>" data-select-url="<c:url value='/rest/selectLeftMusicFolder'/>" data-download-url="<c:url value='/rest/downloadMedia'/>" data-refresh="${param.refresh}" data-csrf-header="<c:out value='${_csrf.headerName}'/>" data-csrf-token="<c:out value='${_csrf.token}'/>" data-main-image="<spring:theme code='playImage'/>" data-add-image="<spring:theme code='addImage'/>" data-add-next-image="<spring:theme code='addNextImage'/>" data-download-image="<spring:theme code='downloadImage'/>" data-all-folders-label="<fmt:message key='left.allfolders'/>" hidden></div>

<div style="padding-bottom:1.5em">
    <a href="home.view" target="main">
      <img src="<spring:theme code='logoImage'/>" style="width:196px" title="<fmt:message key='top.help'/>" alt="">
    </a>
</div>

<div id="leftFolderPicker" style="padding-bottom:1em" hidden><select id="leftMusicFolder" style="width:100%"></select></div>
<div id="leftIndexes" style="margin-bottom:0.5em;padding-left:2px" class="bgcolor1"></div>
<div style="padding-bottom:0.5em"><div class="forward"><a id="leftRefresh" href="left.view"><fmt:message key="common.refresh"/></a></div></div>
<div id="leftShortcuts" hidden><h2 class="bgcolor1" style="padding-left:2px"><fmt:message key="left.shortcut"/></h2><div id="leftShortcutItems"></div></div>

<h2 class="bgcolor1" style="padding-left: 2px"><fmt:message key="left.playlists"/></h2>
<div id="playlistWrapper" style='padding-left:2px'>
    <div id="playlists"></div>
    <div id="playlistOverflow" style="display:none"></div>
    <div style="padding-top: 0.3em"></div>
    <div class="forward" id="showAllPlaylists" style="display: none"><a href="#" onclick="showAllPlaylists()"><fmt:message key="left.showallplaylists"/></a></div>
    <div class="forward"><a href="#" onclick="createEmptyPlaylist()"><fmt:message key="left.createplaylist"/></a></div>
    <div class="forward"><a href="importPlaylist.view" target="main"><fmt:message key="left.importplaylist"/></a></div>
</div>

<div id="leftArtists"></div><div id="leftSongs" style="padding-top:1em"></div>
<div id="leftStatistics" class="detail" style="padding-top:.6em;padding-left:2px" hidden data-format="<fmt:message key='left.statistics'><fmt:param value='{0}'/><fmt:param value='{1}'/><fmt:param value='{2}'/><fmt:param value='{3}'/><fmt:param value='{4}'/></fmt:message>"></div>

<div style="height:5em"></div>


<div class="bgcolor2" style="opacity: 1.0; clear: both; position: fixed; bottom: 0; right: 0; left: 0;
      padding: 0.25em 0.75em 0.25em 0.75em; border-top:1px solid black; max-width: 850px;">
    <div id="leftIndexFooter"></div>
</div>

</body></html>
