<!DOCTYPE html>
<%--@elvariable id="publicSharePreview" type="java.util.Map"--%>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>

<html>
<head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <meta property="og:type" content="album"/>
    <c:if test="${not empty publicSharePreview}">
        <meta property="og:title" content="${fn:escapeXml(publicSharePreview.title)}"/>
        <meta property="og:image" content="${fn:escapeXml(publicSharePreview.image)}"/>
    </c:if>
    <script type="text/javascript" src="<c:url value='/script/mediaelement/mediaelement-and-player.min.js'/>"></script>
    <script type="text/javascript" src="<c:url value='/script/mediaelement/plugins/playlist/playlist.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/speed/speed.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/speed/speed-i18n.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/quality/quality.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/quality/quality-i18n.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/chromecast/chromecast.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/plugins/chromecast/chromecast-i18n.js'/>"></script>

    <link type="text/css" rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/playlist/playlist.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/speed/speed.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/quality/quality.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement/plugins/chromecast/chromecast.min.css'/>">

</head>

<body class="mainframe bgcolor1" style="height:100vh;margin:0;">
<div class="external box">
    <div class="header">
        <h1 id="shareTitle">Loading…</h1>
        <h2 id="shareSubtitle" style="margin:0;"></h2>
    </div>
    <div id="sharePlayer"></div>
    <span id="publicShareData" data-url="${fn:escapeXml(publicShareDataUrl)}" hidden></span>

    <div class="detail" style="text-align:center;">Streaming by <a href="https://airsonic.github.io/"
								   rel="noopener noreferrer"
                                                                   target="_blank"><b>Airsonic</b></a></div>

</div>

<script src="<c:url value='/script/public-share.js'/>?v=1"></script>
<script>loadPublicShare(document);</script>
<style>
    .external .mejs-container.mejs-audio, .external .mejs-container.mejs-video, .mejs__container.mejs__audio, .mejs__container.mejs__video {
        margin: auto;
        margin-top: 2%;
        margin-bottom: 2%;
        flex-grow: 1;
        flex-shrink: 1;
        flex-basis: auto;
    }
    .external.box {
        display: flex;
        flex-flow: column;
        height: 100%;
    }
    .external > .header {
        padding-top: 2em;
        margin: auto;
        width: 500px;
        flex-grow: 0;
        flex-shrink: 1;
        flex-basis: auto;
    }
    .external > .detail {
        flex-grow: 0;
        flex-shrink: 1;
        flex-basis: 40px;
    }
</style>
</body>
</html>
