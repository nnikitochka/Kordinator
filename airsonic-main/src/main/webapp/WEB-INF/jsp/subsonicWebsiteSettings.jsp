<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<script src="<c:url value='/script/subsonic-website-settings.js?v=3'/>"></script>
<script>
    var websiteSettingsApi = createWebsiteSettings({
        readUrl: "<c:url value='/rest/getWebsiteSettings'/>",
        url: "<c:url value='/rest/updateWebsiteSettings'/>",
        csrfHeader: "${_csrf.headerName}",
        csrfToken: "${_csrf.token}"
    });
</script>
