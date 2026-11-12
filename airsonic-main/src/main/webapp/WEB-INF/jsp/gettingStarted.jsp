<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html><head>
    <%@ include file="head.jsp" %>
    <script defer src="<c:url value='/script/getting-started.js?v=1'/>"></script>
</head>
<body class="mainframe bgcolor1" onload="loadGettingStarted(document)">
<c:set var="hideMessage"><fmt:message key="gettingStarted.hidealert"/></c:set>
<div id="gettingStartedData" data-url="<c:url value='/rest/gettingStarted'/>" data-home="<c:url value='/home.view'/>" data-csrf-header="<c:out value='${_csrf.headerName}'/>" data-csrf-token="<c:out value='${_csrf.token}'/>" data-message="<c:out value='${hideMessage}'/>" hidden></div>
<p id="gettingStartedError" role="alert" class="warning" hidden><fmt:message key="gettingStarted.error"/></p>

<h1 style="padding-bottom:0.5em">
    <img src="<spring:theme code='homeImage'/>" alt="">
    <fmt:message key="gettingStarted.title"/>
</h1>

<fmt:message key="gettingStarted.text"/>

<h2 id="gettingStartedRoot" class="warning" hidden><fmt:message key="gettingStarted.root"/></h2>

<table style="padding-top:1em;padding-bottom:2em;width:60%">
    <tr>
        <td style="font-size:26pt;padding:20pt">1</td>
        <td>
            <div style="font-size:14pt"><a href="userSettings.view?userIndex=0"><fmt:message key="gettingStarted.step1.title"/></a></div>
            <div style="padding-top:5pt"><fmt:message key="gettingStarted.step1.text"/></div>
        </td>
    </tr>
    <tr>
        <td style="font-size:26pt;padding:20pt">2</td>
        <td>
            <div style="font-size:14pt"><a href="musicFolderSettings.view"><fmt:message key="gettingStarted.step2.title"/></a></div>
            <div style="padding-top:5pt"><fmt:message key="gettingStarted.step2.text"/></div>
        </td>
    </tr>
    <tr>
        <td style="font-size:26pt;padding:20pt">3</td>
        <td>
            <div style="font-size:14pt"><fmt:message key="gettingStarted.step3.title"/></div>
            <div style="padding-top:5pt"><fmt:message key="gettingStarted.step3.text"/></div>
        </td>
    </tr>

</table>

<div class="forward"><a id="gettingStartedHide" href="#" hidden><fmt:message key="gettingStarted.hide"/></a></div>

</body></html>
