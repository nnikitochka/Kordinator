<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script src="<c:url value='/script/subsonic-upload-status.js'/>"></script>
<script>
    function watchUploads(onUpdate) {
        watchUploadStatus({
            url: "<c:url value='/rest/getUploadStatus'/>",
            csrfHeader: "${_csrf.headerName}",
            csrfToken: "${_csrf.token}"
        }, onUpdate);
    }
</script>
