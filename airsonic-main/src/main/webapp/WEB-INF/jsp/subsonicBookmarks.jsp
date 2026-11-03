<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script src="<c:url value='/script/subsonic-bookmarks.js?v=2'/>"></script>
<script>
var bookmarkApi = createSubsonicBookmarks({
    listUrl: "<c:url value='/rest/getBookmarks'/>",
    createUrl: "<c:url value='/rest/createBookmark'/>",
    deleteUrl: "<c:url value='/rest/deleteBookmark'/>",
    csrfHeader: "${_csrf.headerName}",
    csrfToken: "${_csrf.token}",
    onError: function () {
        $().toastmessage("showErrorToast", "<fmt:message key='bookmarks.error'/>");
    }
});
</script>
