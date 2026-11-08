<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
</head>
<body class="mainframe bgcolor1">
<script type="text/javascript" src="<c:url value='/script/wz_tooltip.js'/>"></script>
<script type="text/javascript" src="<c:url value='/script/tip_balloon.js'/>"></script>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="transcoding"/>
    <c:param name="toast" value="${settings_toast}"/>
</c:import>

<p id="transcodingSettingsError" class="warning" role="alert"></p>
<p id="transcodingSettingsSaved" role="status" hidden><fmt:message key="common.settingssaved"/></p>
<form id="transcodingSettings">
<fieldset id="transcodingFields" disabled style="border:0;padding:0">
<table class="indent">
    <thead><tr>
        <th><fmt:message key="transcodingsettings.name"/></th>
        <th><fmt:message key="transcodingsettings.sourceformat"/></th>
        <th><fmt:message key="transcodingsettings.targetformat"/></th>
        <th><fmt:message key="transcodingsettings.step1"/></th>
        <th><fmt:message key="transcodingsettings.step2"/></th>
        <th style="padding-left:1em"><fmt:message key="common.delete"/></th>
    </tr></thead>

    <tbody id="transcodingRows"></tbody>


    <tbody><tr>
        <th colspan="6" align="left" style="padding-top:1em"><fmt:message key="transcodingsettings.add"/></th>
    </tr>

    <tr>
        <td><input class="monospace" name="name" size="10" placeholder="<fmt:message key='transcodingsettings.name'/>"/></td>
        <td><input class="monospace" name="sourceFormats" size="36" placeholder="<fmt:message key='transcodingsettings.sourceformat'/>"/></td>
        <td><input class="monospace" name="targetFormat" size="10" placeholder="<fmt:message key='transcodingsettings.targetformat'/>"/></td>
        <td><input class="monospace" name="step1" size="60" placeholder="<fmt:message key='transcodingsettings.step1'/>"/></td>
        <td><input class="monospace" name="step2" size="22" placeholder="<fmt:message key='transcodingsettings.step2'/>"/></td>
        <td/>
    </tr>

    <tr>
        <td colspan="6" style="padding-top:0.1em">
            <input type="checkbox" id="defaultActive" name="defaultActive" checked/>
            <label for="defaultActive"><fmt:message key="transcodingsettings.defaultactive"/></label>
        </td>
    </tr>
</tbody></table>


    <table style="white-space:nowrap" class="indent">
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.downsamplecommand"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="downsamplecommand"/></c:import>
            </td>
            <td>
                <input class="monospace" type="text" name="downsampleCommand" size="100"/>
            </td>
        </tr>
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.hlscommand"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="hlscommand"/></c:import>
            </td>
            <td>
                <input class="monospace" type="text" name="hlsCommand" size="100"/>
            </td>
        </tr>
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.jukeboxcommand"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="jukeboxcommand"/></c:import>
            </td>
            <td>
                <input class="monospace" type="text" name="jukeboxCommand" size="100"/>
            </td>
        </tr>
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.videoimagecommand"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="videoimagecommand"/></c:import>
            </td>
            <td>
                <input class="monospace" type="text" name="videoImageCommand" size="100"/>
            </td>
        </tr>
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.subtitlesextractioncommand"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="subtitlesextractioncommand"/></c:import>
            </td>
            <td>
                <input class="monospace" type="text" name="subtitlesExtractionCommand" size="100"/>
            </td>
        </tr>
    </table>

    <table style="white-space:nowrap" class="indent">
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.transcodeestimatetimepadding"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="transcodeestimatetimepadding"/></c:import>
            </td>
            <td>
                <input class="monospace" name="transcodeEstimateTimePadding" size="8"/>
            </td>
        </tr>
        <tr>
            <td style="font-weight: bold;">
                <fmt:message key="advancedsettings.transcodeestimatebytepadding"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="transcodeestimatebytepadding"/></c:import>
            </td>
            <td>
                <input class="monospace" name="transcodeEstimateBytePadding" size="8"/>
            </td>
        </tr>
    </table>

    <p style="padding-top:0.75em">
        <input type="submit" value="<fmt:message key='common.save'/>" style="margin-right:0.3em">
        <a href='nowPlaying.view'><input type="button" value="<fmt:message key='common.cancel'/>" style="margin-right:1.3em"></a>
        <a href="https://airsonic.github.io/docs/transcode/" target="_blank" rel="noopener noreferrer"><fmt:message key="transcodingsettings.recommended"/></a>
    </p>

</fieldset>
</form>



<div style="width:80%">
    <fmt:message key="transcodingsettings.info"><fmt:param><span id="transcodeDirectory"></span></fmt:param><fmt:param><span id="transcodingBrand"></span></fmt:param></fmt:message>
</div>
<script src="<c:url value='/script/transcoding-settings.js'/>"></script>
<script>
initTranscodingSettings({
    readUrl: '<c:url value="/rest/getTranscodingSettings"/>',
    saveUrl: '<c:url value="/rest/saveTranscodingSettings"/>',
    csrfHeader: '${_csrf.headerName}',
    csrfToken: '${_csrf.token}'
});
</script>
</body></html>
