<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>
</head>

<body class="mainframe bgcolor1">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="dlna"/>
</c:import>

<p id="dlnaSettingsError" class="warning" role="alert"></p>
<p id="dlnaSettingsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<form id="dlnaSettings">
<fieldset id="dlnaFields" disabled style="border:0;padding:0">

    <div>
        <input type="checkbox" name="dlnaEnabled" id="dlnaEnabled"/>
        <label for="dlnaEnabled"><fmt:message key="dlnasettings.enabled"/></label>
    </div>
    <p class="detail" style="width:60%;white-space:normal">
        <fmt:message key="dlnasettings.description"/>
    </p>

    <div>
        <fmt:message key="dlnasettings.servername"/>
        <input name="dlnaServerName" id="dlnaServerName" size="40"/>
    </div>
    <p class="detail" style="width:60%;white-space:normal;padding-top:0">
        <fmt:message key="dlnasettings.servername.description"/>
    </p>

    <div>
        <label for="dlnaBaseLANURL" ><fmt:message key="dlnasettings.baselanurl"/></label>
        <input type="text" size="50" name="dlnaBaseLANURL" id="dlnaBaseLANURL"/>
    </div>
    <p class="detail" style="width:60%;white-space:normal">
        <fmt:message key="dlnasettings.lanurl.description"/>
    </p>

    <p>
        <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
        <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>"></a>
    </p>

</fieldset>
</form>
<script src="<c:url value='/script/dlna-settings.js'/>"></script>
<script>
initDlnaSettings({
    readUrl: '<c:url value="/rest/getDlnaSettings"/>',
    saveUrl: '<c:url value="/rest/saveDlnaSettings"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>

</body></html>
