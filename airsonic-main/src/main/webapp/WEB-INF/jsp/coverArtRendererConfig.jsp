<%@ include file="include.jsp" %>
<c:url value="/coverArt.view" var="coverArtRendererUrl"/>
<c:url value="/changeCoverArt.view" var="coverArtRendererChangeUrl"/>
<c:url value="/main.view" var="coverArtRendererAlbumUrl"/>
<c:url value="/playlist.view" var="coverArtRendererPlaylistUrl"/>
<spring:theme code="playOverlayImage" var="coverArtRendererPlayOverlay"/>
<spring:theme code="addOverlayImage" var="coverArtRendererAddOverlay"/>
<c:url value="${coverArtRendererPlayOverlay}" var="coverArtRendererPlayOverlayUrl"/>
<c:url value="${coverArtRendererAddOverlay}" var="coverArtRendererAddOverlayUrl"/>
<div id="coverArtRendererConfig" hidden
     data-cover-art-url="<c:out value='${coverArtRendererUrl}'/>"
     data-change-cover-art-url="<c:out value='${coverArtRendererChangeUrl}'/>"
     data-album-page-url="<c:out value='${coverArtRendererAlbumUrl}'/>"
     data-playlist-page-url="<c:out value='${coverArtRendererPlaylistUrl}'/>"
     data-play-overlay-url="<c:out value='${coverArtRendererPlayOverlayUrl}'/>"
     data-add-overlay-url="<c:out value='${coverArtRendererAddOverlayUrl}'/>">
    <span id="coverArtChangeLabel"><fmt:message key="coverart.change"/></span>
    <span id="coverArtZoomLabel"><fmt:message key="coverart.zoom"/></span>
    <span id="coverArtPlaylistSongsLabel"><fmt:message key="playlist2.songs"/></span>
</div>
<script src="<c:url value='/script/cover-art-renderer.js?v=1'/>"></script>
<script>
    window.coverArtRenderer = createCoverArtRenderer({
        coverArtUrl: document.getElementById("coverArtRendererConfig").dataset.coverArtUrl,
        changeCoverArtUrl: document.getElementById("coverArtRendererConfig").dataset.changeCoverArtUrl,
        albumPageUrl: document.getElementById("coverArtRendererConfig").dataset.albumPageUrl,
        playlistPageUrl: document.getElementById("coverArtRendererConfig").dataset.playlistPageUrl,
        playOverlayUrl: document.getElementById("coverArtRendererConfig").dataset.playOverlayUrl,
        addOverlayUrl: document.getElementById("coverArtRendererConfig").dataset.addOverlayUrl,
        changeLabel: document.getElementById("coverArtChangeLabel").textContent,
        zoomLabel: document.getElementById("coverArtZoomLabel").textContent,
        playlistSongsLabel: document.getElementById("coverArtPlaylistSongsLabel").textContent
    });
</script>
