<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<html><head>
    <%@ include file="head.jsp" %>
    <script src="<c:url value='/script/create-share.js?v=1'/>"></script>
    <script>
      document.addEventListener('DOMContentLoaded', function () {
        bindCreateShare(document, {
          url: "<c:url value='/rest/createWebShare'/>", query: window.location.search,
          csrfHeader: '${_csrf.headerName}', csrfToken: '${_csrf.token}'
        });
      });
    </script>
</head>
<body class="mainframe bgcolor1">
<h1>
    <img src="<spring:theme code='shareImage'/>" alt="" style="height:18px;">
    <span style="vertical-align:middle"><fmt:message key="share.title"/></span>
</h1>
<p id="shareError" class="warning" role="alert" hidden></p>
<button id="shareRetry" type="button" hidden><fmt:message key="common.ok"/></button>
<div id="shareResult" hidden>
    <fmt:message key="share.warning"/>
    <p id="shareFacebook">
        <a target="_blank" rel="noopener noreferrer"><img src="<spring:theme code='shareFacebookImage'/>" alt=""></a>&nbsp;
        <a target="_blank" rel="noopener noreferrer"><fmt:message key="share.facebook"/></a>
    </p>
    <p id="shareTwitter">
        <a target="_blank" rel="noopener noreferrer"><img src="<spring:theme code='shareTwitterImage'/>" alt=""></a>&nbsp;
        <a target="_blank" rel="noopener noreferrer"><fmt:message key="share.twitter"/></a>
    </p>
    <p id="shareLink"><fmt:message key="share.link"><fmt:param>#</fmt:param></fmt:message></p>
</div>
<div style="padding-top:1em">
    <div class="back" style="float:left;padding-right:10pt"><a id="shareBack" href="main.view"><fmt:message key="common.back"/></a></div>
    <sec:authorize access="hasRole('SETTINGS')">
        <div class="forward" style="float:left"><a href="shareSettings.view"><fmt:message key="share.manage"/></a></div>
    </sec:authorize>
</div>
</body></html>
