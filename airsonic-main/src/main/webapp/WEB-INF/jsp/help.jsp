<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<html><head>
    <%@ include file="head.jsp" %>
    <script defer src="<c:url value='/script/server-info.js?v=1'/>"></script>
</head>
<body class="mainframe bgcolor1" onload="loadServerInfo(document)">
<c:set var="helpTitle"><fmt:message key="help.title"><fmt:param value="{0}"/></fmt:message></c:set>
<div id="serverInfoData" data-info-url="<c:url value='/rest/serverInfo'/>" data-log-url="<c:url value='/rest/serverLog'/>" data-unknown="<fmt:message key='common.unknown'/>" hidden></div>
<h1>
    <img src="<spring:theme code='helpImage'/>" alt="">
    <span id="serverTitle" data-template="<c:out value='${helpTitle}'/>" style="vertical-align:middle"></span>
</h1>
<p id="serverInfoError" hidden role="alert" class="warning"><fmt:message key="help.error"/></p>
<p id="serverUpgrade" hidden class="warning"><fmt:message key="help.upgrade"><fmt:param value="<span data-server-brand=''></span>"/><fmt:param value="<span id='serverLatestVersion'></span>"/><fmt:param value="#"/></fmt:message></p>
<table width="75%" class="ruleTable indent">
    <tr><td class="ruleTableHeader"><fmt:message key="help.version.title"/></td><td class="ruleTableCell"><span id="serverVersion"></span> &ndash; <span id="serverBuildDate"></span> [Commit: <a id="serverBuildNumber" target="_blank" rel="noopener noreferrer"></a>]</td></tr>
    <tr><td class="ruleTableHeader"><fmt:message key="help.server.title"/></td><td id="serverRuntime" class="ruleTableCell"></td></tr>
    <tr><td class="ruleTableHeader"><fmt:message key="help.license.title"/></td><td class="ruleTableCell">
        <a href="https://www.gnu.org/copyleft/gpl.html" target="_blank" rel="noopener noreferrer"><img style="float:right;margin-left:10px" alt="GPL 3.0" src="<c:url value='/icons/default_light/gpl.png'/>"></a>
        <fmt:message key="help.license.text"><fmt:param value="<span data-server-brand=''></span>"/></fmt:message></td></tr>
    <tr><td class="ruleTableHeader"><fmt:message key="help.homepage.title"/></td><td class="ruleTableCell"><a target="_blank" href="https://airsonic.github.io/" rel="noopener noreferrer">Airsonic website</a></td></tr>
    <tr><td class="ruleTableHeader"><fmt:message key="help.forum.title"/></td><td class="ruleTableCell"><a target="_blank" href="https://www.reddit.com/r/airsonic" rel="noopener noreferrer">Airsonic on Reddit</a></td></tr>
    <tr><td class="ruleTableHeader"><fmt:message key="help.contact.title"/></td><td class="ruleTableCell"><fmt:message key="help.contact.text"><fmt:param value="<span data-server-brand=''></span>"/></fmt:message></td></tr>
</table>
<section id="serverLogSection" hidden>
    <h2><img src="<spring:theme code='logImage'/>" alt=""> <span style="vertical-align:middle"><fmt:message key="help.log"/></span></h2>
    <p id="serverLogError" hidden role="alert" class="warning"><fmt:message key="help.logerror"/></p>
    <table cellpadding="2" class="log indent"><tbody id="serverLogEntries"></tbody></table>
    <p><fmt:message key="help.logfile"><fmt:param value="<span id='serverLogFile'></span>"/></fmt:message></p>
</section>
<div class="forward"><a id="serverRefresh" href="<c:url value='/help.view'/>"><fmt:message key="common.refresh"/></a></div>
</body></html>
