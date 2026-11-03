<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script src="<c:url value='/script/subsonic-now-playing.js'/>?v=2"></script>
<script>
function watchNowPlaying(owner, callback) {
    return watchSubsonicNowPlaying({
        baseUrl: "<c:url value='/rest/'/>", csrfHeader: "${_csrf.headerName}",
        csrfToken: "${_csrf.token}"
    }, callback);
}
</script>
