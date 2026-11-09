<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>
<html>
<head>
    <%@ include file="head.jsp" %>
    <script defer src="<c:url value='/script/password-recovery.js?v=1'/>"></script>
</head>
<body class="mainframe bgcolor1" onload="loadPasswordRecovery(document)">
<form id="recoveryForm" action="<c:url value='/rest/recover'/>" method="POST">
    <div class="bgcolor2 shadow" style="padding:20px 50px; margin:100px 50px 0">
        <div style="margin-left:auto; margin-right:auto; width:45em">
            <h1><fmt:message key="recover.title"/></h1>
            <p style="padding-top:1em; padding-bottom:0.5em"><fmt:message key="recover.text"/></p>
            <div id="recoveryInputs">
                <input type="text" id="usernameOrEmail" autofocus name="usernameOrEmail" style="width:18em; margin-right:1em">
                <input id="recoverySubmit" type="submit" disabled value="<fmt:message key='recover.send'/>">
                <div id="recoveryCaptcha" class="g-recaptcha" style="margin-top:1em"></div>
            </div>
            <c:set var="recoverySuccess"><fmt:message key="recover.success"><fmt:param value="{0}"/></fmt:message></c:set>
            <p id="recoverySuccess" hidden role="status" data-template="<c:out value='${recoverySuccess}'/>" style="padding-top:1em"></p>
            <c:forEach var="error" items="usernotfound,noemail,sendfailed,invalidcaptcha">
                <p hidden data-recovery-error="recover.error.${error}" role="alert" class="warning" style="padding-top:1em"><fmt:message key="recover.error.${error}"/></p>
            </c:forEach>
            <div class="back" style="margin-top:1.5em"><a href="<c:url value='/login.view'/>"><fmt:message key="common.back"/></a></div>
        </div>
    </div>
</form>
</body>
</html>
