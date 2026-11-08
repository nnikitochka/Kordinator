<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>


<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>

    <script type="text/javascript" language="javascript">
        var lastfmCredsAbsent = true;
        var listenBrainzCredsAbsent = true;
        function enableFields() {
            $("#lastFm").is(":checked") ? $(".lastFmRow").show() : $(".lastFmRow").hide();
            if (lastfmCredsAbsent) {
                $(".lastFmRow .nocreds").show();
            } else {
                $(".lastFmRow .nocreds").hide();
            }
            $("#listenBrainz").is(":checked") ? $(".listenBrainzRow").show() : $(".listenBrainzRow").hide();
            if (listenBrainzCredsAbsent) {
                $(".listenBrainzRow .nocreds").show();
            } else {
                $(".listenBrainzRow .nocreds").hide();
            }
        }
    </script>
</head>

<body class="mainframe bgcolor1" onload="enableFields()">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="personal"/>
    <c:param name="restricted" value="${not playerSettingsAdmin}"/>
    <c:param name="toast" value="${param.saved}"/>
</c:import>

<fmt:message key="personalsettings.title" var="title"><fmt:param>${personalSettingsUsername}</fmt:param></fmt:message>
<h2>${fn:escapeXml(title)}</h2>

<fmt:message key="common.default" var="defaultTitle"/>
<p id="personalSettingsError" class="warning" role="alert"></p>
<p id="personalSettingsSaved" role="status" ${param.saved ? '' : 'hidden'}><fmt:message key="common.settingssaved"/></p>
<form id="personalSettings">
<fieldset id="personalFields" disabled>
<input type="hidden" name="playlistVisibility.entryTypeVisible"/>
<input type="hidden" name="playqueueVisibility.entryTypeVisible"/>

    <table style="white-space:nowrap" class="indent">

        <tr>
            <td><fmt:message key="personalsettings.language"/></td>
            <td>
                <select name="locale" style="width:15em"><option value="">${fn:escapeXml(defaultTitle)}</option></select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="language"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="personalsettings.theme"/></td>
            <td>
                <select name="themeId" style="width:15em"><option value="">${fn:escapeXml(defaultTitle)}</option></select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="theme"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="personalsettings.albumlist"/></td>
            <td>
                <select name="albumListId" style="width:15em">
                    <c:forTokens items="random,newest,starred,highest,frequent,recent,decade,genre,alphabetical" delims="," var="albumList">
                        <c:set var="label">
                            <fmt:message key="home.${albumList}.title"/>
                        </c:set>
                        <option value="${albumList}">${fn:escapeXml(label)}</option>
                    </c:forTokens>
                </select>
            </td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <th style="padding:0 0.5em 0.5em 0;text-align:left;"><fmt:message key="personalsettings.display"/></th>
            <th style="padding:0 0.5em 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.browse"/></th>
            <th style="padding:0 0 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.playlist"/></th>
            <th style="padding:0 0 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.playqueue"/></th>
            <th style="padding:0 0 0.5em 0.5em">
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="visibility"/></c:import>
            </th>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.header"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.headerVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.headerVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.headerVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.tracknumber"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.trackNumberVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.trackNumberVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.trackNumberVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.discnumber"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.discNumberVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.discNumberVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.discNumberVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.artist"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.artistVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.artistVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.artistVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.albumartist"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.albumArtistVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.albumArtistVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.albumArtistVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.album"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.albumVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.albumVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.albumVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.genre"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.genreVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.genreVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.genreVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.year"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.yearVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.yearVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.yearVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.bitrate"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.bitRateVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.bitRateVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.bitRateVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.duration"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.durationVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.durationVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.durationVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.format"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.formatVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.formatVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.formatVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.filesize"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.fileSizeVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.fileSizeVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.fileSizeVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.playcount"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.playCountVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.playCountVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.playCountVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.lastplayed"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.lastPlayedVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.lastPlayedVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.lastPlayedVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.lastscanned"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.lastScannedVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.lastScannedVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.lastScannedVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.created"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.createdVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.createdVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.createdVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.changed"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.changedVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playlistVisibility.changedVisible" class="checkbox"/></td>
            <td style="text-align:center"><input type="checkbox" name="playqueueVisibility.changedVisible" class="checkbox"/></td>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.entrytype"/></td>
            <td style="text-align:center"><input type="checkbox" name="mainVisibility.entryTypeVisible" class="checkbox"/></td>
            <td style="text-align:center"></td>
            <td style="text-align:center"></td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td><input type="checkbox" name="showNowPlayingEnabled" id="nowPlaying" class="checkbox"/></td>
            <td><label for="nowPlaying"><fmt:message key="personalsettings.shownowplaying"/></label></td>
            <td style="padding-left:2em"><input type="checkbox" name="showArtistInfoEnabled" id="artistInfo" class="checkbox"/></td>
            <td><label for="artistInfo"><fmt:message key="personalsettings.showartistinfo"/></label></td>
        </tr>
        <tr>
            <td><input type="checkbox" name="nowPlayingAllowed" id="nowPlayingAllowed" class="checkbox"/></td>
            <td><label for="nowPlayingAllowed"><fmt:message key="personalsettings.nowplayingallowed"/></label></td>
            <td style="padding-left:2em"><input type="checkbox" name="autoHidePlayQueue" id="autoHidePlayQueue" class="checkbox"/></td>
            <td><label for="autoHidePlayQueue"><fmt:message key="personalsettings.autohideplayqueue"/></label></td>
            <td style="padding-left:2em"><input type="checkbox" name="partyModeEnabled" id="partyModeEnabled" class="checkbox"/></td>
            <td><label for="partyModeEnabled"><fmt:message key="personalsettings.partymode"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="partymode"/></c:import>
            </td>
        </tr>
        <tr>
            <td><input type="checkbox" name="queueFollowingSongs" id="queueFollowingSongs" class="checkbox"/></td>
            <td><label for="queueFollowingSongs"><fmt:message key="personalsettings.queuefollowingsongs"/></label></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td><input type="checkbox" name="finalVersionNotificationEnabled" id="final" class="checkbox"/></td>
            <td><label for="final"><fmt:message key="personalsettings.finalversionnotification"/></label></td>
        </tr>
        <tr>
            <td><input type="checkbox" name="betaVersionNotificationEnabled" id="beta" class="checkbox"/></td>
            <td><label for="beta"><fmt:message key="personalsettings.betaversionnotification"/></label></td>
        </tr>
        <tr>
            <td><input type="checkbox" name="songNotificationEnabled" id="song" class="checkbox"/></td>
            <td><label for="song"><fmt:message key="personalsettings.songnotification"/></label></td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td><input type="checkbox" name="lastFmEnabled" id="lastFm" class="checkbox" onclick="enableFields()"/></td>
            <td><label for="lastFm"><fmt:message key="personalsettings.lastfmenabled"/></label></td>
            <td></td>
        </tr>
        <tr id="lastFmStatus" class="lastFmRow">
            <td></td>
            <td colspan="2">
              <span><fmt:message key="personalsettings.modifycredsincreds" /></span>
              <span class="nocreds warning"><fmt:message key="personalsettings.credsnotpresent" /></span>
            </td>
        </tr>
        <tr>
            <td><input type="checkbox" name="listenBrainzEnabled" id="listenBrainz" class="checkbox" onclick="enableFields()"/></td>
            <td><label for="listenBrainz"><fmt:message key="personalsettings.listenbrainzenabled"/></label></td>
            <td></td>
        </tr>
        <tr class="listenBrainzRow">
            <td></td>
            <td><fmt:message key="personalsettings.listenbrainzurl"/></td>
            <td><input type="text" name="listenBrainzUrl" size="36"/></td>
        </tr>
        <tr id="listenBrainzStatus" class="listenBrainzRow">
            <td></td>
            <td colspan="2">
              <span><fmt:message key="personalsettings.modifycredsincreds" /></span>
              <span class="nocreds warning"><fmt:message key="personalsettings.credsnotpresent" /></span>
            </td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td><input type="checkbox" name="autoBookmark" id="autoBookmark" class="checkbox"/></td>
            <td><label for="autoBookmark"><fmt:message key="personalsettings.autobookmark"/></label><c:import url="helpToolTip.jsp"><c:param name="topic" value="autobookmark"/></c:import></td>
        </tr>
        <tr>
            <td colspan="2"><fmt:message key="personalsettings.audiobookmarkfrequency"/></td>
            <td><input type="text" name="audioBookmarkFrequency" size="10"/></td>
        </tr>
        <tr>
            <td colspan="2"><fmt:message key="personalsettings.videobookmarkfrequency"/></td>
            <td><input type="text" name="videoBookmarkFrequency" size="10"/></td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td colspan="2"><fmt:message key="personalsettings.searchcount"/></td>
            <td><input type="text" name="searchCount" size="10"/></td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td><input type="checkbox" name="keyboardShortcutsEnabled" id="keyboardShortcutsEnabled" class="checkbox"/></td>
            <td><label for="keyboardShortcutsEnabled"><fmt:message key="personalsettings.keyboardshortcutsenabled"/></label></td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <th></th>
            <th style="padding:0 0.5em 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.browse"/> (<fmt:message key="personalsettings.files"/>)</th>
            <th style="padding:0 0.5em 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.browse"/> (<fmt:message key="personalsettings.folders"/>)</th>
            <th style="padding:0 0 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.playlist"/></th>
            <th style="padding:0 0 0.5em 0.5em;text-align:center;"><fmt:message key="personalsettings.playqueue"/></th>
            <th style="padding:0 0 0.5em 0.5em;text-align:center;"><fmt:message key="top.bookmarks"/></th>
        </tr>
        <tr>
            <td><fmt:message key="personalsettings.paginationsize"/></td>
            <td style="text-align:center"><input type="text" name="paginationSizeFiles" size="10"/></td>
            <td style="text-align:center"><input type="text" name="paginationSizeFolders" size="10"/></td>
            <td style="text-align:center"><input type="text" name="paginationSizePlaylist" size="10"/></td>
            <td style="text-align:center"><input type="text" name="paginationSizePlayqueue" size="10"/></td>
            <td style="text-align:center"><input type="text" name="paginationSizeBookmarks" size="10"/></td>
        </tr>
    </table>

    <p style="padding-top:1em;padding-bottom:1em">
        <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em"/>
        <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>"></a>
    </p>

    <h2><fmt:message key="personalsettings.avatar.title"/></h2>

    <p style="padding-top:1em">
        <span id="personalAvatars"></span>
    </p>
    <p>
        <input type="radio" id="noAvatar" name="avatarId" value="-1"/>
        <label for="noAvatar"><fmt:message key="personalsettings.avatar.none"/></label>
    </p>
    <p>
        <input type="radio" id="customAvatar" name="avatarId" value="-2"/>
        <label for="customAvatar"><fmt:message key="personalsettings.avatar.custom"/>
            <span id="personalCustomAvatar"></span>
        </label>
    </p>
</fieldset></form>

<p id="avatarUploadError" class="warning" role="alert"></p>
<p id="avatarUploadSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<form id="avatarUpload" enctype="multipart/form-data">
<fieldset id="avatarUploadFields" disabled>
    <table>
        <tr>
            <td style="padding-right:1em"><fmt:message key="personalsettings.avatar.changecustom"/></td>
            <td style="padding-right:1em"><input type="file" id="file" name="file" size="40"/></td>
            <td style="padding-right:1em"><input type="submit" value="<fmt:message key='personalsettings.avatar.upload'/>"/></td>
        </tr>
    </table>
</fieldset></form>

<p class="detail" style="text-align:right">
    <fmt:message key="personalsettings.avatar.courtesy"/>
</p>

<script src="<c:url value='/script/personal-settings.js?v=2'/>"></script>
<script>
initPersonalSettings({
    readUrl: '<c:url value="/rest/getPersonalSettings"/>',
    saveUrl: '<c:url value="/rest/savePersonalSettings"/>',
    uploadUrl: '<c:url value="/rest/uploadAvatar"/>',
    avatarUrl: '<c:url value="/rest/getAvatar"/>',
    pageUrl: '<c:url value="/personalSettings.view"/>',
    indexUrl: '<c:url value="/index.view"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>

</body></html>
