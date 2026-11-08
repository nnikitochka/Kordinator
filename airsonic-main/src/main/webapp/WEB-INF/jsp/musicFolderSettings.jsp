<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>

    <script src="<c:url value='/script/music-folder-settings.js'/>"></script>
</head>
<body class="mainframe bgcolor1">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="musicFolder"/>
</c:import>

<div id="folderSettingsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></div>
<div id="folderSettingsError" class="warning" role="alert"></div>
<form id="folderSettings"><fieldset id="folderFields" disabled style="border:0;padding:0;margin:0">

<table class="indent">
    <tr>
        <th><fmt:message key="musicfoldersettings.name"/></th>
        <th><fmt:message key="musicfoldersettings.path"/></th>
        <th><fmt:message key="status.type"/></th>
        <th><fmt:message key="musicfoldersettings.enabled"/></th>
        <th><fmt:message key="common.delete"/></th>
        <th></th>
    </tr>

    <tbody id="musicFolders"></tbody>

    <tr>
        <th colspan="6" align="left" style="padding-top:1em"><fmt:message key="musicfoldersettings.deleted"/></th>
    </tr>

    <tbody id="deletedMusicFolders"></tbody>

    <tr>
        <td colspan="6" align="left" style="padding-top:1em"><span class="detail"><fmt:message key="musicfoldersettings.deletenote"/></span></td>
    </tr>

        <tr>
            <th colspan="6" align="left" style="padding-top:1em"><fmt:message key="musicfoldersettings.add"/></th>
        </tr>

    <tr>
        <td><input id="newMusicFolderName" name="newMusicFolder.name" style="width:90%"></td>
        <td><input id="newMusicFolderPath" name="newMusicFolder.path" style="width:90%"></td>
        <td align="center">
          <select id="newMusicFolderType" name="newMusicFolder.type"></select>
        </td>
        <td align="center"><input type="checkbox" name="newMusicFolder.enabled" class="checkbox"></td>
        <td></td>
        <td></td>
    </tr>

</table>

    <p class="forward"><a href="userSettings.view"><fmt:message key="musicfoldersettings.access"/></a></p>
    <p class="detail" style="width:60%;white-space:normal;margin-top:-10px;">
        <fmt:message key="musicfoldersettings.access.description"/>
    </p>

    <div>
    <fmt:message key="musicfoldersettings.uploadsfolder"/>
    <input name="uploadsFolder" size="70">
        <c:import url="helpToolTip.jsp"><c:param name="topic" value="uploadsfolderpattern"/></c:import>
    </div>

    <div>
    <fmt:message key="musicfoldersettings.excludepattern"/>
    <input name="excludePatternString" size="70">
        <c:import url="helpToolTip.jsp"><c:param name="topic" value="excludepattern"/></c:import>
    </div>

    <div>
    <input type="checkbox" name="ignoreSymLinks" id="ignoreSymLinks">
        <label for="ignoreSymLinks"><fmt:message key="musicfoldersettings.ignoresymlinks"/></label>
    </div>

    <div>
        <input type="checkbox" name="fullScan" class="checkbox" id="fullScan">
        <label for="fullScan"><fmt:message key="musicfoldersettings.fullscan"/></label>
        <c:import url="helpToolTip.jsp"><c:param name="topic" value="fullscan"/></c:import>
    </div>
    <div style="margin-left:1em;">
        <input type="checkbox" name="clearFullScanSettingAfterScan" class="checkbox" id="clearFullScanSettingAfterScan">
        <label for="clearFullScanSettingAfterScan"><fmt:message key="musicfoldersettings.fullscanclear"/></label>
        <c:import url="helpToolTip.jsp"><c:param name="topic" value="clearfullscan"/></c:import>
    </div>

    <div style="padding-top: 0.5em;padding-bottom: 0.3em">
        <span style="white-space: nowrap">
            <fmt:message key="musicfoldersettings.scan"/>
            <select name="interval">
                <fmt:message key="musicfoldersettings.interval.never" var="never"/>
                <fmt:message key="musicfoldersettings.interval.one" var="one"/>
                <option value="-1"><c:out value="${never}"/></option>
                <option value="1"><c:out value="${one}"/></option>

                <c:forTokens items="2 3 7 14 30 60" delims=" " var="interval">
                    <fmt:message key="musicfoldersettings.interval.many" var="many"><fmt:param value="${interval}"/></fmt:message>
                    <option value="${interval}"><c:out value="${many}"/></option>
                </c:forTokens>
            </select>
            <select name="hour">
                <c:forEach begin="0" end="23" var="hour">
                    <fmt:message key="musicfoldersettings.hour" var="hourLabel"><fmt:param value="${hour}"/></fmt:message>
                    <option value="${hour}"><c:out value="${hourLabel}"/></option>
                </c:forEach>
            </select>
        </span>
    </div>

    <table>
        <tr>
            <td><div class="forward"><button type="button" id="scanNow"><fmt:message key="musicfoldersettings.scannow"/></button></div></td>
            <td><c:import url="helpToolTip.jsp"><c:param name="topic" value="scanMediaFolders"/></c:import></td>
        </tr>
    </table>

        <p id="scanning" hidden style="width:60%"><b><fmt:message key="musicfoldersettings.nowscanning"/></b></p>

    <div>
        <input type="checkbox" name="fastCache" class="checkbox" id="fastCache">
        <label for="fastCache"><fmt:message key="musicfoldersettings.fastcache"/></label>
    </div>

    <p class="detail" style="width:60%;white-space:normal;">
        <fmt:message key="musicfoldersettings.fastcache.description"/>
    </p>

    <p class="forward"><button type="button" id="expunge"><fmt:message key="musicfoldersettings.expunge"/></button></p>
    <p class="detail" style="width:60%;white-space:normal;margin-top:-10px;">
        <fmt:message key="musicfoldersettings.expunge.description"/>
    </p>

    <p >
        <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
        <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>"></a>
    </p>

</fieldset></form>

<span id="folderNotFound" hidden><fmt:message key="musicfoldersettings.notfound"/></span>
<span id="folderOverlap" hidden><fmt:message key="musicfoldersettings.overlap"><fmt:param value=""/></fmt:message></span>
<script>initMusicFolderSettings({readUrl: '<c:url value="/rest/getMusicFolderSettings"/>', saveUrl: '<c:url value="/rest/saveMusicFolderSettings"/>', scanUrl: '<c:url value="/rest/startScan"/>', expungeUrl: '<c:url value="/rest/expungeMusicFolders"/>', statusUrl: '<c:url value="/rest/getScanStatus"/>', csrfHeader: '${_csrf.headerName}', csrfToken: '${_csrf.token}'});</script>
</body></html>
