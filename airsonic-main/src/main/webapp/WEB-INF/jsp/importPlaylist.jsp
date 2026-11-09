<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>

<html><head>
    <%@ include file="head.jsp" %>
    <script src="<c:url value='/script/playlist-import.js?v=1'/>"></script>
    <script>
      document.addEventListener('DOMContentLoaded', function () {
        bindPlaylistImport(document.getElementById('importPlaylistForm'), document.getElementById('importPlaylistError'), {
          url: "<c:url value='/rest/importPlaylist'/>",
          csrfHeader: '${_csrf.headerName}', csrfToken: '${_csrf.token}',
          imported: function (playlist) {
            try { if (top.left && typeof top.left.updatePlaylists === 'function') top.left.updatePlaylists(); }
            catch (error) { console.error(error); }
            window.location.assign('playlist.view?id=' + encodeURIComponent(playlist.id));
          }
        });
      });
    </script>
</head>
<body class="mainframe bgcolor1">

<h1 style="padding-bottom:0.5em">
    <fmt:message key="importPlaylist.title"/>
</h1>

<p id="importPlaylistError" class="warning" hidden></p>

<div style="padding-bottom: 0.25em">
    <fmt:message key="importPlaylist.text"/>
</div>
<form id="importPlaylistForm" method="post" enctype="multipart/form-data" action="<c:url value='/rest/importPlaylist'/>">
    <input type="file" id="file" name="file" size="40" required/>
    <input type="submit" value="<fmt:message key='common.ok'/>"/>
</form>


</body></html>
