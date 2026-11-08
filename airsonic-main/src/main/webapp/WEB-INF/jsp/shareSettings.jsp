<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>


<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
</head>
<body class="mainframe bgcolor1">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="share"/>
    <c:param name="restricted" value="${not playerSettingsAdmin}"/>
</c:import>

<p id="shareSettingsError" class="warning" role="alert"></p>
<p id="shareSettingsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<form id="shareSettings">
<fieldset id="shareFields" disabled>

    <table class="music indent">
        <thead><tr>
            <th style="padding-left:1em"><fmt:message key="sharesettings.name"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.owner"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.description"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.lastvisited"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.visits"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.files"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.expires"/></th>
            <th style="padding-left:1em"><fmt:message key="sharesettings.expirein"/></th>
            <th style="padding-left:1em"><fmt:message key="common.delete"/></th>
        </tr></thead>

        <tbody id="shareRows"></tbody>

    </table>

    <p style="padding-top:1em">
        <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
        <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>" style="margin-right:2.0em"></a>
        <input type="checkbox" id="deleteExpired" name="deleteExpired"/>
        <label for="deleteExpired"><fmt:message key="sharesettings.deleteexpired"/></label>
    </p>

</fieldset></form>

<template id="shareExpirationOptions">
    <label><input type="radio" value="7"><fmt:message key="sharesettings.expirein.week"/></label>
    <label><input type="radio" value="30"><fmt:message key="sharesettings.expirein.month"/></label>
    <label><input type="radio" value="365"><fmt:message key="sharesettings.expirein.year"/></label>
    <label><input type="radio" value="0"><fmt:message key="sharesettings.expirein.never"/></label>
</template>
<script src="<c:url value='/script/share-settings.js'/>"></script>
<script>
initShareSettings({
    readUrl: '<c:url value="/rest/getShares"/>',
    updateUrl: '<c:url value="/rest/updateShare"/>',
    deleteUrl: '<c:url value="/rest/deleteShare"/>',
    mediaUrl: '<c:url value="/main.view"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>
</body></html>
