<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script src="<c:url value='/script/subsonic-playlists.js'/>"></script>
<script>
var playlistApi = createSubsonicPlaylists({
    baseUrl: "<c:url value='/rest/'/>",
    csrfHeader: "${_csrf.headerName}",
    csrfToken: "${_csrf.token}",
    onError: function () {
        $().toastmessage("showErrorToast", "<fmt:message key='playlists.error'/>");
    }
});
</script>
