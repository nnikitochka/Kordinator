<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script src="<c:url value='/script/subsonic-lyrics.js'/>"></script>
    <title><fmt:message key="lyrics.title"/></title>

    <script type="text/javascript" language="javascript">
        var getLyrics = createSubsonicLyrics({
            url: "<c:url value='/rest/getLyrics'/>",
            csrfHeader: "${_csrf.headerName}",
            csrfToken: "${_csrf.token}"
        });

        function init() {
            search();
        }

        function search() {
            getLyrics($('#artist').val(), $('#song').val());
        }
    </script>

</head>
<body class="mainframe bgcolor1" onload="init();">

<form action="#" onsubmit="search();return false;">
    <table>
        <tr>
            <td><fmt:message key="lyrics.artist"/></td>
            <td style="padding-left:0.50em"><input id="artist" type="text" size="40" value="<c:out value='${model.artist}'/>" tabindex="1"/></td>
            <td style="padding-left:0.75em"><input type="submit" value="<fmt:message key='lyrics.search'/>" style="width:6em"
                                                   tabindex="3"/></td>
        </tr>
        <tr>
            <td><fmt:message key="lyrics.song"/></td>
            <td style="padding-left:0.50em"><input id="song" type="text" size="40" value="<c:out value='${model.song}'/>" tabindex="2"/></td>
            <td style="padding-left:0.75em"><input type="button" value="<fmt:message key='common.close'/>" style="width:6em"
                                                   onclick="self.close()" tabindex="4"/></td>
        </tr>
    </table>
</form>
<hr/>
<h2 id="wait"><fmt:message key="lyrics.wait"/></h2>
<h2 id="noLyricsFound" style="display:none"><fmt:message key="lyrics.nolyricsfound"/></h2>
<p id="tryLater" style="display:none"><b><fmt:message key="lyrics.trylater"/></b></p>

<div id="lyrics" style="display:none;">
    <h2 id="lyricsHeader" style="text-align:center;margin-bottom:1em"></h2>

    <div id="lyricsText" style="white-space: pre-wrap;"></div>

    <p class="detail" style="text-align:right">
        <fmt:message key="lyrics.courtesy"/>
    </p>
</div>

<hr/>
<p style="text-align:center">
    <a href="javascript:self.close()">[<fmt:message key="common.close"/>]</a>
</p>

</body>
</html>
