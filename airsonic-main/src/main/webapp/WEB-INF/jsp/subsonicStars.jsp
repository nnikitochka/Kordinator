<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script src="<c:url value='/script/subsonic-stars.js'/>"></script>
<script>
var setMediaFilesStarred = createSubsonicStars({
    starUrl: "<c:url value='/rest/star'/>",
    unstarUrl: "<c:url value='/rest/unstar'/>",
    csrfHeader: "${_csrf.headerName}",
    csrfToken: "${_csrf.token}",
    onError: function () {
        $().toastmessage("showErrorToast", "<fmt:message key='stars.error'/>");
    }
});
</script>
