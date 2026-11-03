<script src="<c:url value='/script/subsonic-now-playing.js'/>"></script>
<script>
function watchNowPlaying(owner, callback) {
    return watchSubsonicNowPlaying({
        baseUrl: "<c:url value='/rest/'/>", csrfHeader: "${_csrf.headerName}",
        csrfToken: "${_csrf.token}", stomp: top.StompClient, owner: owner
    }, callback);
}
</script>
