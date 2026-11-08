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

<script lang="javascript">

function hideAllTechnologyDepends() {
  $('.technologyDepends').hide();
}

function showTechnologyDepends(technologyName) {
  var selector = '.technologyDepends.' + technologyName;
  $(selector).show();
}

$(document).ready(function() {

  $('.technologyRadio').click(function() {
    hideAllTechnologyDepends();
    var technologyName = $(this).val();
    showTechnologyDepends(technologyName);
  });

  hideAllTechnologyDepends();
  $('.technologyRadio:checked').each(function() {
    var technologyName = $(this).val();
    showTechnologyDepends(technologyName);
  });
});

</script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="player"/>
    <c:param name="toast" value="${settings_toast}"/>
    <c:param name="restricted" value="${not playerSettingsAdmin}"/>
</c:import>

<fmt:message key="common.unknown" var="unknown"/>

<p id="playerSettingsError" class="warning" role="alert"></p>
<p id="playerSettingsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<p id="noPlayers" hidden><fmt:message key="playersettings.noplayers"/></p>
<form id="playerSettings">
<fieldset id="playerFields" disabled>
<table class="indent">
<tr><td><b><fmt:message key="playersettings.title"/></b></td><td><select id="playerSelector"></select></td></tr>
<tr><td><button type="button" id="deletePlayer"><fmt:message key="playersettings.forget"/></button></td>
<td><button type="button" id="clonePlayer"><fmt:message key="playersettings.clone"/></button></td></tr>
</table>

            <input type="hidden" name="id"/>

            <table class="ruleTable indent">
                <c:forEach items="${['WEB', 'EXTERNAL', 'EXTERNAL_WITH_PLAYLIST', 'JUKEBOX']}" var="technologyHolder">
                    <c:set var="technologyName">
                        <fmt:message key="playersettings.technology.${fn:toLowerCase(technologyHolder)}.title"/>
                    </c:set>

                    <tr>
                        <td class="ruleTableHeader">
                            <input type="radio" class="technologyRadio" id="radio-${technologyHolder}" name="technologyName" value="${technologyHolder}"/>
                            <b><label for="radio-${technologyHolder}">${technologyName}</label></b>
                        </td>
                        <td class="ruleTableCell" style="width:40em">
                            <fmt:message key="playersettings.technology.${fn:toLowerCase(technologyHolder)}.text"/>
                        </td>
                    </tr>
                </c:forEach>
            </table>

            <table class="indent" style="border-spacing:3pt;">
                <tr>
                    <td style="padding-right: 1.5em"><fmt:message key="playersettings.type"/></td>
                    <td>
                        <span id="playerType">${unknown}</span>
                    </td>
                </tr>
                <tr>
                    <td style="padding-right: 1.5em"><fmt:message key="playersettings.lastseen"/></td>
                    <td><span id="playerLastSeen"></span></td>
                </tr>
            </table>

            <table class="indent" style="border-spacing:3pt;">
                <tr>
                    <td><fmt:message key="playersettings.name"/></td>
                    <td><input name="name" size="16"/></td>
                    <td colspan="2"><c:import url="helpToolTip.jsp"><c:param name="topic" value="playername"/></c:import></td>
                </tr>

                <%// Max bitrate %>
                <tr class="technologyDepends WEB EXTERNAL EXTERNAL_WITH_PLAYLIST JUKEBOX">
                    <td><fmt:message key="playersettings.maxbitrate"/></td>
                    <td>
                        <select name="transcodeSchemeName" style="width:8em"></select>
                    </td>
                    <td>
                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="transcode"/></c:import>
                    </td>
                    <td class="warning">
                        <span id="noTranscoder" hidden>
                            <fmt:message key="playersettings.notranscoder"/>
                        </span>
                    </td>
                </tr>
            </table>

            <table class="indent technologyDepends WEB EXTERNAL EXTERNAL_WITH_PLAYLIST JUKEBOX" style="border-spacing:3pt">
                <tr>
                    <td>
                        <input type="checkbox" name="dynamicIp" id="dynamicIp" class="checkbox"/>
                        <label for="dynamicIp"><fmt:message key="playersettings.dynamicip"/></label>
                    </td>
                    <td><c:import url="helpToolTip.jsp"><c:param name="topic" value="dynamicip"/></c:import></td>
                </tr>

                <tr>
                    <td>
                        <input type="checkbox" name="autoControlEnabled" id="autoControlEnabled" class="checkbox"/>
                        <label for="autoControlEnabled"><fmt:message key="playersettings.autocontrol"/></label>
                    </td>
                    <td><c:import url="helpToolTip.jsp"><c:param name="topic" value="autocontrol"/></c:import></td>
                </tr>

                <tr>
                    <td>
                        <input type="checkbox" name="m3uBomEnabled" id="m3uBomEnabled" class="checkbox"/>
                        <label for="m3uBomEnabled"><fmt:message key="playersettings.m3ubom"/></label>
                    </td>
                    <td><c:import url="helpToolTip.jsp"><c:param name="topic" value="m3ubom"/></c:import></td>
                </tr>
            </table>

            <div class="indent technologyDepends WEB EXTERNAL EXTERNAL_WITH_PLAYLIST JUKEBOX">
                <b><fmt:message key="playersettings.transcodings"/></b>
                <div id="playerTranscodings"></div>
            </div>

            <input type="submit" value="<fmt:message key='common.save'/>" style="margin-top:1em;margin-right:0.3em">
            <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>" style="margin-top:1em"></a>
</fieldset></form>

<script src="<c:url value='/script/player-settings.js'/>"></script>
<script>
initPlayerSettings({
    readUrl: '<c:url value="/rest/getPlayerSettings"/>',
    saveUrl: '<c:url value="/rest/savePlayerSettings"/>',
    cloneUrl: '<c:url value="/rest/clonePlayer"/>',
    deleteUrl: '<c:url value="/rest/deletePlayer"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>
</body></html>
