<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%@ include file="include.jsp" %>

<%--
Creates HTML for displaying the rating stars.
PARAMETERS
  id: Album ID. May be null if readonly.
  readonly: Whether rating can be changed.
  rating: The rating, an integer from 0 (no rating), through 10 (lowest rating), to 50 (highest rating).
--%>

<c:forEach var="i" begin="1" end="5">

    <c:choose>
        <c:when test="${param.rating ge i * 10}">
            <spring:theme code="ratingOnImage" var="imageUrl"/>
        </c:when>
        <c:when test="${param.rating ge i*10 - 7 and param.rating le i*10 - 3}">
            <spring:theme code="ratingHalfImage" var="imageUrl"/>
        </c:when>
        <c:otherwise>
            <spring:theme code="ratingOffImage" var="imageUrl"/>
        </c:otherwise>
    </c:choose>

    <c:choose>
        <c:when test="${param.readonly}">
            <img style="height:18px" src="${imageUrl}" style="margin-right:-3px;height:18px;" alt="" title="<fmt:message key='rating.rating'/> ${param.rating/10}">
        </c:when>
        <c:otherwise>
            <button type="button" data-media-id="<c:out value='${param.id}'/>" data-media-rating="${i}" style="border:0;padding:0;background:transparent;cursor:pointer"><img src="${imageUrl}" style="margin-right:-3px;height:18px" alt="" title="<fmt:message key='rating.rating'/> ${i}"></button>
        </c:otherwise>
    </c:choose>

</c:forEach>

<c:if test="${not param.readonly}">
    &nbsp;| <button type="button" data-media-id="<c:out value='${param.id}'/>" data-media-rating="0" style="border:0;padding:0;background:transparent;cursor:pointer"><img src="<spring:theme code='clearRatingImage'/>" alt="" title="<fmt:message key='rating.clearrating'/>" style="margin-right:5px;height:18px"></button>
</c:if>
