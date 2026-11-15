<%@ page contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>
<%@ include file="include.jsp" %>
<spring:theme code="ratingOnImage" var="ratingRendererOn"/>
<spring:theme code="ratingHalfImage" var="ratingRendererHalf"/>
<spring:theme code="ratingOffImage" var="ratingRendererOff"/>
<spring:theme code="clearRatingImage" var="ratingRendererClear"/>
<c:url value="${ratingRendererOn}" var="ratingRendererOnUrl"/>
<c:url value="${ratingRendererHalf}" var="ratingRendererHalfUrl"/>
<c:url value="${ratingRendererOff}" var="ratingRendererOffUrl"/>
<c:url value="${ratingRendererClear}" var="ratingRendererClearUrl"/>
<div id="ratingRendererConfig" hidden
     data-rating-on-image="<c:out value='${ratingRendererOnUrl}'/>"
     data-rating-half-image="<c:out value='${ratingRendererHalfUrl}'/>"
     data-rating-off-image="<c:out value='${ratingRendererOffUrl}'/>"
     data-clear-rating-image="<c:out value='${ratingRendererClearUrl}'/>">
    <span id="ratingLabel"><fmt:message key="rating.rating"/></span>
    <span id="clearRatingLabel"><fmt:message key="rating.clearrating"/></span>
</div>
<script>
    window.ratingRendererConfig = {
        ratingOnImage: document.getElementById("ratingRendererConfig").dataset.ratingOnImage,
        ratingHalfImage: document.getElementById("ratingRendererConfig").dataset.ratingHalfImage,
        ratingOffImage: document.getElementById("ratingRendererConfig").dataset.ratingOffImage,
        clearRatingImage: document.getElementById("ratingRendererConfig").dataset.clearRatingImage,
        ratingLabel: document.getElementById("ratingLabel").textContent,
        clearRatingLabel: document.getElementById("clearRatingLabel").textContent
    };
</script>
