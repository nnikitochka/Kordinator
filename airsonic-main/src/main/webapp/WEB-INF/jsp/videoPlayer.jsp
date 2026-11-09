<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>

<html>
<head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script src="<c:url value='/script/subsonic-stars.js'/>"></script>
<script src="<c:url value='/script/subsonic-bookmarks.js?v=3'/>"></script>
    <script type="text/javascript" src="<c:url value='/script/mediaelement/mediaelement-and-player.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/speed/speed.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/speed/speed-i18n.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/quality/quality.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/quality/quality-i18n.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/chromecast/chromecast.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/chromecast/chromecast-i18n.js'/>"></script>
    <link rel="stylesheet" type="text/css" href="<c:url value='/style/videoPlayer.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/speed/speed.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/quality/quality.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/chromecast/chromecast.min.css'/>">

    <style type="text/css">
        .ui-slider .ui-slider-handle {
            width: 11px;
            height: 11px;
            cursor: pointer;
        }
        .ui-slider a {
            outline:none;
        }
        .ui-slider {
            cursor: pointer;
        }
        #videoPlayer {
            height: 100%;
            width: 100%;
        }
    </style>

    <script src="<c:url value='/script/video-player.js?v=1'/>"></script>
</head>

<body class="mainframe bgcolor1" style="padding-bottom:0.5em" onload="loadVideoPlayer(document);">

<spring:theme code="ratingOnImage" var="starOnImage"/>
<spring:theme code="ratingOffImage" var="starOffImage"/>
<div id="videoPlayerData" hidden data-url="<c:url value='/rest/videoPlayer'/>"
     data-share-url="<c:url value='/createShare.view'/>" data-download-url="<c:url value='/rest/downloadMedia'/>"
     data-back-url="<c:url value='/main.view'/>"
     data-star-url="<c:url value='/rest/star'/>" data-unstar-url="<c:url value='/rest/unstar'/>"
     data-bookmarks-url="<c:url value='/rest/getBookmarks'/>" data-bookmark-create-url="<c:url value='/rest/createBookmark'/>"
     data-bookmark-delete-url="<c:url value='/rest/deleteBookmark'/>"
     data-star-on="${fn:escapeXml(starOnImage)}" data-star-off="${fn:escapeXml(starOffImage)}"
     data-hls-url="<c:url value='/script/mediaelement/renderers/hls-1.0.10/hls.min.js'/>"
     data-dash-url="<c:url value='/script/mediaelement/renderers/dash.all-4.0.1.min.js'/>"></div>
<div style="width:100%;height:100%;display:flex;flex-direction:column;">
    <div style="flex:0 1 auto"><video id="videoPlayer" controls preload="metadata"></video></div>
    <h1 id="videoTitle" style="padding-top:1em;padding-bottom:0.5em;"></h1>
    <p id="videoError" role="alert"></p>
    <div>
        <img id="share" hidden src="<spring:theme code='shareImage'/>" title="<fmt:message key='main.more.share'/>" alt="<fmt:message key='main.more.share'/>">
        <img id="download" hidden src="<spring:theme code='downloadImage'/>" title="<fmt:message key='common.download'/>" alt="<fmt:message key='common.download'/>">
        <img id="starImage" hidden title="<fmt:message key='playlist.more.star'/> / <fmt:message key='playlist.more.unstar'/>" alt="<fmt:message key='playlist.more.star'/> / <fmt:message key='playlist.more.unstar'/>">
    </div>
    <div class="back" style="float:left;padding-right:2em;margin-top:1em;"><a id="videoBack"><fmt:message key="common.back"/></a></div>
</div>
</body>
</html>
