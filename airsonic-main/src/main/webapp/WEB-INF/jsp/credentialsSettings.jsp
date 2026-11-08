<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
</head>
<body class="mainframe bgcolor1">
<script src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script src="<c:url value='/script/tip_balloon.js'/>"></script>
<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="credentials"/>
    <c:param name="restricted" value="${not playerSettingsAdmin}"/>
</c:import>
<h2>Credentials Management</h2>
<p id="credentialsError" class="warning" role="alert"></p>
<p id="credentialsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<form id="credentials">
<fieldset id="credentialsFields" disabled>
<table id="credentialsTable">
    <thead><tr>
        <th>ID</th>
        <th><fmt:message key="credentialsettings.app"/></th>
        <th><fmt:message key="credentialsettings.user"/></th>
        <th><fmt:message key="credentialsettings.comments"/></th>
        <th><fmt:message key="credentialsettings.created"/></th>
        <th><fmt:message key="credentialsettings.updated"/></th>
        <th><fmt:message key="credentialsettings.encoder"/></th>
        <th><fmt:message key="credentialsettings.expires"/><c:import url="helpToolTip.jsp"><c:param name="topic" value="credentialsdates"/></c:import></th>
        <th><fmt:message key="common.delete"/></th>
    </tr></thead>
    <tbody><tr><td colspan="9">Airsonic Credentials <c:import url="helpToolTip.jsp"><c:param name="topic" value="credentialsairsonic"/></c:import></td></tr></tbody>
    <tbody id="airsonicCredentials"></tbody>
    <tbody><tr id="ldapCredentialWarning" hidden><td colspan="9" class="warning"><fmt:message key="credentialsettings.ldapauthenabledforuser"/></td></tr>
    <tr><td colspan="9">Third-party Credentials <c:import url="helpToolTip.jsp"><c:param name="topic" value="credentialsthirdparty"/></c:import></td></tr></tbody>
    <tbody id="thirdPartyCredentials"></tbody>
</table>
<p><fmt:message key="credentialsettings.immutable"/></p>
<input type="submit" value="<fmt:message key='common.save'/>">
<button type="button" id="createcredsbutton"><fmt:message key="credentialsettings.addcredentials"/></button>
<input type="reset" value="<fmt:message key='common.cancel'/>">
</fieldset></form>

<div id="createNewCreds" hidden>
<h3><fmt:message key="credentialsettings.addcredentials"/></h3>
<form id="newCreds">
<fieldset id="newCredentialsFields" disabled>
<table>
    <tr><td><fmt:message key="credentialsettings.app"/></td><td><select name="app" id="app"></select></td></tr>
    <tr><td><fmt:message key="login.username"/></td><td><input name="appUsername" id="appUsername" size="20" autocomplete="off"></td></tr>
    <tr><td><fmt:message key="credentialsettings.encoder"/></td><td><select name="encoder" id="encoder"></select></td></tr>
    <tr><td><fmt:message key="usersettings.newpassword"/></td><td><input type="password" name="credential" autocomplete="new-password" size="20"></td></tr>
    <tr><td><fmt:message key="usersettings.confirmpassword"/></td><td><input type="password" name="confirmCredential" autocomplete="new-password" size="20"></td></tr>
    <tr><td><fmt:message key="credentialsettings.expires"/></td><td><input type="datetime-local" name="expires" step="0.001"></td></tr>
</table>
<input type="submit" value="<fmt:message key='common.create'/>">
<button type="button" id="cancelNewCredential"><fmt:message key="common.cancel"/></button>
</fieldset></form>
</div>

<c:if test="${playerSettingsAdmin}">
<h3><fmt:message key="credentialsettings.admincontrols"/></h3>
<form id="credentialAdmin">
<fieldset id="credentialAdminFields" disabled>
<h4><fmt:message key="credentialsettings.systemchecks"/></h4>
<div id="legacyCredentials" hidden>
<p><fmt:message key="credentialsettings.legacycredspresent"/></p>
<button type="submit" name="action" value="migrateDefault"><fmt:message key="credentialsettings.adminmigratelegacytononlegacydefault"/></button>
<button type="submit" name="action" value="migrateDecodable"><fmt:message key="credentialsettings.adminmigratelegacytononlegacydecodableonly"/></button>
</div>
<p id="openCredentials" class="warning" hidden><fmt:message key="credentialsettings.opencredspresent"/></p>
<p id="defaultAdminCredentials" class="warning" hidden><fmt:message key="credentialsettings.defaultadmincredspresent"/></p>
<h4><fmt:message key="credentialsettings.encoders"/></h4>
<table>
    <tr><td><fmt:message key="credentialsettings.nondecodableencoder"/></td><td><select name="nonDecodableEncoder"></select></td></tr>
    <tr><td><fmt:message key="credentialsettings.decodableencoder"/></td><td><select name="decodableEncoder"></select></td></tr>
    <tr><td><label for="preferNonDecodable"><fmt:message key="credentialsettings.prefernondecodablepasswords"/></label></td><td><input type="checkbox" name="preferNonDecodable" id="preferNonDecodable"></td></tr>
</table>
<button type="submit" name="action" value="encoders"><fmt:message key="common.save"/></button>
<h4>Keys</h4>
<p><fmt:message key="credentialsettings.keybackup"/></p>
<button type="button" id="showCredentialKeys"><fmt:message key="credentialsettings.showkeys"/></button>
<table>
    <tr><td>JWT Key <c:import url="helpToolTip.jsp"><c:param name="topic" value="credentialsjwtkey"/></c:import></td>
        <td><input type="password" name="jwtKey" autocomplete="new-password"></td><td><button type="submit" name="action" value="jwtKey"><fmt:message key="common.save"/></button></td></tr>
    <tr><td>Encryption Key Password <c:import url="helpToolTip.jsp"><c:param name="topic" value="credentialsencryptionkey"/></c:import></td>
        <td><input type="password" name="encryptionKey" autocomplete="new-password"></td><td><button type="submit" name="action" value="encryptionKey"><fmt:message key="common.save"/></button></td></tr>
    <tr><td>Encryption Key Salt</td><td colspan="2"><span id="encryptionKeySalt"></span></td></tr>
</table>
</fieldset></form>
</c:if>
<script src="<c:url value='/script/credentials-settings.js'/>"></script>
<script>
initCredentialsSettings({
    readUrl: '<c:url value="/rest/getCredentials"/>',
    createUrl: '<c:url value="/rest/createCredential"/>',
    updateUrl: '<c:url value="/rest/updateCredentials"/>',
    adminUrl: '<c:url value="/rest/updateCredentialSettings"/>',
    keysUrl: '<c:url value="/rest/getCredentialKeys"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>
</body></html>
