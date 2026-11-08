<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script src="<c:url value='/script/user-administration.js'/>"></script>
</head>
<body class="mainframe bgcolor1">
<script src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script src="<c:url value='/script/tip_balloon.js'/>"></script>
<c:import url="settingsHeader.jsp"><c:param name="cat" value="user"/></c:import>
<table class="indent"><tr><td><b><fmt:message key="usersettings.title"/></b></td><td>
<select id="userSelector"><option value="">-- <fmt:message key="usersettings.newuser"/> --</option></select>
</td></tr></table><p/>
<div id="userSettingsError" class="warning" role="alert"></div>
<form id="command"><fieldset id="userFields" disabled style="border:0;padding:0;margin:0">
<input type="hidden" name="newUser" value="true">
<table>
            <tr id="adminRow"><td><input type="checkbox" class="checkbox" name="adminRole" id="admin"></td><td><label for="admin"><fmt:message key="usersettings.admin"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="settingsRole" id="settings"></td><td><label for="settings"><fmt:message key="usersettings.settings"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="streamRole" id="stream"></td><td><label for="stream"><fmt:message key="usersettings.stream"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="jukeboxRole" id="jukebox"></td><td><label for="jukebox"><fmt:message key="usersettings.jukebox"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="downloadRole" id="download"></td><td><label for="download"><fmt:message key="usersettings.download"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="uploadRole" id="upload"></td><td><label for="upload"><fmt:message key="usersettings.upload"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="shareRole" id="share"></td><td><label for="share"><fmt:message key="usersettings.share"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="coverArtRole" id="coverArt"></td><td><label for="coverArt"><fmt:message key="usersettings.coverart"/></label></td></tr>
            <tr><td><input type="checkbox" class="checkbox" name="commentRole" id="comment"></td><td><label for="comment"><fmt:message key="usersettings.comment"/></label></td></tr>
</table>
<div id="folderSection" hidden><h2><fmt:message key="usersettings.folderaccess"/></h2><table><tbody id="musicFolders"></tbody></table></div>
<table class="indent"><tr><td><fmt:message key="playersettings.maxbitrate"/></td><td><select name="transcodeSchemeName" style="width:8em"></select></td>
<td><c:import url="helpToolTip.jsp"><c:param name="topic" value="transcode"/></c:import></td>
<td id="transcodeWarning" class="warning" hidden><fmt:message key="playersettings.notranscoder"/></td></tr></table>
<table id="deleteSection" class="indent" hidden><tr><td><input type="checkbox" name="deleteUser" id="delete" class="checkbox"></td><td><label for="delete"><fmt:message key="usersettings.delete"/></label></td></tr></table>
<table id="ldapSection" hidden><tr><td><input type="checkbox" name="ldapAuthenticated" id="ldapAuthenticated" class="checkbox"></td><td><label for="ldapAuthenticated"><fmt:message key="usersettings.ldap"/></label></td><td><c:import url="helpToolTip.jsp"><c:param name="topic" value="ldap"/></c:import></td></tr></table>
<table class="indent"><tr><td><fmt:message key="usersettings.username"/></td><td><input name="username" style="width:15em"></td></tr>
<tr><td><fmt:message key="usersettings.email"/></td><td><input name="email" style="width:20em"></td></tr></table>
<table id="passwordChangeCheckboxTable" hidden><tr><td><input type="checkbox" name="passwordChange" id="passwordChange" class="checkbox"></td><td><label for="passwordChange"><fmt:message key="credentialsettings.addcredentials"/> <fmt:message key="credentialsettings.credentialsnotremoved"/></label></td><td><c:import url="helpToolTip.jsp"><c:param name="topic" value="credentialsadd"/></c:import></td></tr></table>
<table id="passwordChangeTable" hidden><tr><td><fmt:message key="usersettings.password"/></td><td><input type="password" name="password"></td></tr>
<tr><td><fmt:message key="usersettings.confirmpassword"/></td><td><input type="password" name="confirmPassword"></td></tr></table>
<input type="submit" value="<fmt:message key='common.save'/>" style="margin-top:1.5em;margin-right:0.3em">
<a href="nowPlaying.view"><input type="button" value="<fmt:message key='common.cancel'/>" style="margin-top:1.5em"></a>
</fieldset></form>
<script>initUserAdministration({readUrl: '<c:url value="/rest/getWebsiteUserAdministration"/>', saveUrl: '<c:url value="/rest/saveWebsiteUser"/>', csrfHeader: '${_csrf.headerName}', csrfToken: '${_csrf.token}'});</script>
</body></html>
