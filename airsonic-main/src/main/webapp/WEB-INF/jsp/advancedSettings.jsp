<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>


<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>
    <script type="text/javascript" language="javascript">
        function enableLdapFields() {
            $("#ldap").is(":checked") ? $("#ldapTable").show() : $("#ldapTable").hide();
        }
    </script>
</head>

<body class="mainframe bgcolor1" onload="enableLdapFields()">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="advanced"/>
    <c:param name="toast" value="${settings_toast}"/>
</c:import>

<p id="advancedSettingsError" class="warning" role="alert"></p>
<p id="advancedSettingsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<form id="advancedSettings">
<fieldset id="advancedFields" disabled style="border:0;padding:0">

    <table style="white-space:nowrap" class="indent">
        <tr>
            <td><fmt:message key="advancedsettings.downloadlimit"/></td>
            <td>
                <input type="text" name="downloadLimit" size="8"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="downloadlimit"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.uploadlimit"/></td>
            <td>
                <input type="text" name="uploadLimit" size="8"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="uploadlimit"/></c:import>
            </td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td><fmt:message key="advancedsettings.smtpFrom"/></td>
            <td>
                <input type="text" name="smtpFrom" size="50"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpFrom"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.smtpServer"/></td>
            <td>
                <input type="text" name="smtpServer" size="50"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpServer"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.smtpPort"/></td>
            <td>
                <input type="text" name="smtpPort" size="5"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpPort"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.smtpEncryption"/></td>
            <td>
                <select name="smtpEncryption" style="width:8em">
                    <fmt:message key="advancedsettings.smtpEncryption.none" var="none"/>
                    <fmt:message key="advancedsettings.smtpEncryption.starttls" var="starttls"/>
                    <fmt:message key="advancedsettings.smtpEncryption.ssl" var="ssl"/>

                    <option value="None">None</option>
                    <option value="STARTTLS">STARTTLS</option>
                    <option value="SSL/TLS">SSL/TLS</option>
                </select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpEncryption"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.smtpUser"/></td>
            <td>
                <input type="text" name="smtpUser" size="20"/>
                <fmt:message key="advancedsettings.smtpPassword"/>
                <input type="password" name="smtpPassword" size="20"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpCredentials"/></c:import>
            </td>
        </tr>
    </table>

    <table class="indent">
        <tr>
            <td colspan="2">
                <input type="checkbox" name="captchaEnabled" id="enablecaptcha"/>
                <label for="enablecaptcha">
                    <fmt:message key="advancedsettings.enableCaptcha"/>
                </label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="captcha"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.recaptchaSiteKey"/></td>
            <td>
                <input type="text" name="recaptchaSiteKey" size="50"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="recaptchaSiteKey"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.recaptchaSecretKey"/></td>
            <td>
                <input type="password" name="recaptchaSecretKey" size="50"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="recaptchaSecretKey"/></c:import>
            </td>
        </tr>
    </table>

    <table class="indent"><tr><td>
        <input type="checkbox" name="ldapEnabled" id="ldap" onclick="enableLdapFields()"/>
        <label for="ldap"><fmt:message key="advancedsettings.ldapenabled"/></label>
        <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldap"/></c:import>
    </td></tr></table>

    <table class="indent" id="ldapTable" style="padding-left:2em;padding-bottom: 1em">
        <tr>
            <td><fmt:message key="advancedsettings.ldapurl"/></td>
            <td colspan="3">
                <input type="text" name="ldapUrl" size="70"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapurl"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.ldapsearchfilter"/></td>
            <td colspan="3">
                <input type="text" name="ldapSearchFilter" size="70"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapsearchfilter"/></c:import>
            </td>
        </tr>

        <tr>
            <td><fmt:message key="advancedsettings.ldapmanagerdn"/></td>
            <td>
                <input type="text" name="ldapManagerDn" size="20"/>
            </td>
            <td><fmt:message key="advancedsettings.ldapmanagerpassword"/></td>
            <td>
                <input type="password" name="ldapManagerPassword" size="20"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapmanagerdn"/></c:import>
            </td>
        </tr>

        <tr>
            <td colspan="5">
                <input type="checkbox" name="ldapAutoShadowing" id="ldapAutoShadowing" class="checkbox"/>
                <label for="ldapAutoShadowing"><fmt:message key="advancedsettings.ldapautoshadowing"><fmt:param><span id="advancedBrand"></span></fmt:param></fmt:message></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapautoshadowing"/></c:import>
            </td>
        </tr>
    </table>

    <p class="warning"><fmt:message key="advancedsettings.ldapRequiresRestart"/></p>

    <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
    <a href="nowPlaying.view"><input type="button" value="<fmt:message key='common.cancel'/>"></a>

</fieldset>
</form>

<script src="<c:url value='/script/advanced-settings.js'/>"></script>
<script>
initAdvancedSettings({
    readUrl: '<c:url value="/rest/getAdvancedSettings"/>',
    saveUrl: '<c:url value="/rest/saveAdvancedSettings"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>
</body></html>
