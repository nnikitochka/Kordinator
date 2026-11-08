<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>
    <script src="<c:url value='/script/general-settings.js'/>"></script>
</head>

<body class="mainframe bgcolor1">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="general"/>
    <c:param name="toast" value="${settings_toast}"/>
</c:import>

<p id="generalSettingsError" role="alert" class="warning"></p>
<p id="generalSettingsSaved" role="status" <c:if test="${param.saved ne 'true'}">hidden</c:if>><fmt:message key="common.settingssaved"/></p>
<form id="generalSettings"><fieldset id="generalFields" disabled>

    <table style="white-space:nowrap" class="indent">

        <tr>
            <td><fmt:message key="generalsettings.musicmask"/></td>
            <td>
                <input name="musicFileTypes" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="musicmask"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.videomask"/></td>
            <td>
                <input name="videoFileTypes" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="videomask"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.coverartmask"/></td>
            <td>
                <input name="coverArtFileTypes" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="coverartmask"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.playlistfolder"/></td>
            <td>
                <input name="playlistFolder" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="playlistfolder"/></c:import>
            </td>
        </tr>

        <tr><td colspan="2">&nbsp;</td></tr>

        <tr>
            <td><fmt:message key="generalsettings.index"/></td>
            <td>
                <input name="index" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="index"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.ignoredarticles"/></td>
            <td>
                <input name="ignoredArticles" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ignoredarticles"/></c:import>
            </td>
        </tr>
        
        <tr>
            <td><fmt:message key="generalsettings.genreseparators"/></td>
            <td>
                <input name="genreSeparators" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="genreseparators"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.shortcuts"/></td>
            <td>
                <input name="shortcuts" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="shortcuts"/></c:import>
            </td>
        </tr>

        <tr><td colspan="2">&nbsp;</td></tr>

        <tr>
            <td><fmt:message key="generalsettings.language"/></td>
            <td>
                <select name="locale" style="width:15em"></select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="language"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.theme"/></td>
            <td>
                <select name="themeId" style="width:15em"></select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="theme"/></c:import>
            </td>
        </tr>

        <tr><td colspan="2">&nbsp;</td></tr>
        <tr>
            <td><fmt:message key="generalsettings.coverartsource"/></td>
            <td>
                <select name="coverArtSource" style="width:15em">
                    <option value="FILE_TAG"><fmt:message key="generalsettings.coverartsource.option.filetag"/></option>
                    <option value="TAG_FILE"><fmt:message key="generalsettings.coverartsource.option.tagfile"/></option>
                    <option value="FILE"><fmt:message key="generalsettings.coverartsource.option.file"/></option>
                    <option value="TAG"><fmt:message key="generalsettings.coverartsource.option.tag"/></option>
                </select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="coverartsource"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.coverartquality"/></td>
            <td>
                <input name="coverArtQuality" size="15">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="coverartquality"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="generalsettings.coverartconcurrency"/></td>
            <td>
                <input name="coverArtConcurrency" size="15">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="coverartconcurrency"/></c:import>
            </td>
        </tr>

        <tr><td colspan="2">&nbsp;</td></tr>

        <tr>
            <td>
            </td>
            <td>
                <input type="checkbox" name="sortAlbumsByYear" id="sortAlbumsByYear">
                <label for="sortAlbumsByYear"><fmt:message key="generalsettings.sortalbumsbyyear"/></label>
            </td>
        </tr>
        <tr>
            <td>
            </td>
            <td>
                <input type="checkbox" name="gettingStartedEnabled" id="gettingStartedEnabled">
                <label for="gettingStartedEnabled"><fmt:message key="generalsettings.showgettingstarted"/></label>
            </td>
        </tr>

        <tr><td colspan="2">&nbsp;</td></tr>

        <tr>
            <td><fmt:message key="generalsettings.welcometitle"/></td>
            <td>
                <input name="welcomeTitle" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="welcomemessage"/></c:import>
            </td>
        </tr>
        <tr>
            <td><fmt:message key="generalsettings.welcomesubtitle"/></td>
            <td>
                <input name="welcomeSubtitle" size="70">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="welcomemessage"/></c:import>
            </td>
        </tr>
        <tr>
            <td style="vertical-align:top;"><fmt:message key="generalsettings.welcomemessage"/></td>
            <td>
                <textarea name="welcomeMessage" rows="5" cols="70"></textarea>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="welcomemessage"/></c:import>
            </td>
        </tr>
        <tr>
            <td style="vertical-align:top;"><fmt:message key="generalsettings.loginmessage"/></td>
            <td>
                <textarea name="loginMessage" rows="5" cols="70"></textarea>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="loginmessage"/></c:import>
            </td>
        </tr>
        <tr>
            <td><fmt:message key="generalsettings.sessionduration"/></td>
            <td>
                <input name="sessionDuration" size="15">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="sessionduration"/></c:import>
            </td>
        </tr>

        <tr>
            <td colspan="2" style="padding-top:1.5em">
                <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
                <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>"></a>
            </td>
        </tr>

    </table>
</fieldset></form>

<script>initGeneralSettings({pageUrl: '<c:url value="/generalSettings.view"/>', readUrl: '<c:url value="/rest/getGeneralSettings"/>', saveUrl: '<c:url value="/rest/saveGeneralSettings"/>', csrfHeader: '${_csrf.headerName}', csrfToken: '${_csrf.token}'});</script>
</body></html>
