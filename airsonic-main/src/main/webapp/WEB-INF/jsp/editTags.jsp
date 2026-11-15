<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script src="<c:url value='/script/subsonic-tag-edit.js'/>"></script>
<script type="text/javascript">
    var tagEditApi = createTagEdit({
        url: "<c:url value='/rest/editTags'/>", csrfHeader: "${_csrf.headerName}", csrfToken: "${_csrf.token}"
    });
    var tagEditorDataUrl = "<c:url value='/rest/tagEditor'/>";
    var index = 0;
    var fileCount = 0;
    var editorMessages = {
        working: "<fmt:message key='edittags.working'/>",
        skipped: "<fmt:message key='edittags.skipped'/>",
        updated: "<fmt:message key='edittags.updated'/>",
        error: "<fmt:message key='edittags.error'/>",
        loadError: "<fmt:message key='edittags.error'/>"
    };
    function input(name, value, size, type) {
        var field = document.createElement("input");
        field.type = type || "text";
        field.name = name;
        field.value = value == null ? "" : value;
        if (size) field.size = size;
        return field;
    }
    function cell(row, child) {
        var td = document.createElement("td");
        td.className = "ruleTableCell";
        td.appendChild(child);
        row.appendChild(td);
        return td;
    }
    function addSong(song, songIndex) {
        var row = document.createElement("tr");
        var name = song.fileName || "";
        var file = document.createElement("span");
        file.textContent = name.length > 50 ? name.substring(0, 47) + "..." : name;
        file.title = name;
        var fileCell = cell(row, file);
        fileCell.appendChild(input("id" + songIndex, song.id, 0, "hidden"));
        fileCell.appendChild(input("suggestedTitle" + songIndex, song.suggestedTitle, 0, "hidden"));
        fileCell.appendChild(input("originalTitle" + songIndex, song.title, 0, "hidden"));
        fileCell.appendChild(input("suggestedTrack" + songIndex, song.suggestedTrack, 0, "hidden"));
        fileCell.appendChild(input("originalTrack" + songIndex, song.track, 0, "hidden"));
        cell(row, input("track" + songIndex, song.track, 5));
        cell(row, input("title" + songIndex, song.title, 30));
        cell(row, input("artist" + songIndex, song.artist, 15));
        cell(row, input("album" + songIndex, song.album, 15));
        cell(row, input("year" + songIndex, song.year, 5));
        var genre = input("genre" + songIndex, song.genre);
        genre.style.width = "7em";
        cell(row, genre);
        var status = document.createElement("div"); status.id = "status" + songIndex;
        cell(row, status);
        document.getElementById("songs").appendChild(row);
    }
    function setArtist() { var value = $("input[name='artistAll']").val(); for (var i = 0; i < fileCount; i++) $("input[name='artist" + i + "']").val(value); }
    function setAlbum() { var value = $("input[name='albumAll']").val(); for (var i = 0; i < fileCount; i++) $("input[name='album" + i + "']").val(value); }
    function setYear() { var value = $("input[name='yearAll']").val(); for (var i = 0; i < fileCount; i++) $("input[name='year" + i + "']").val(value); }
    function setGenre() { var value = $("select[name='genreAll']").val(); for (var i = 0; i < fileCount; i++) $("input[name='genre" + i + "']").val(value); }
    function suggestTitle() { for (var i = 0; i < fileCount; i++) $("input[name='title" + i + "']").val($("input[name='suggestedTitle" + i + "']").val()); }
    function resetTitle() { for (var i = 0; i < fileCount; i++) $("input[name='title" + i + "']").val($("input[name='originalTitle" + i + "']").val()); }
    function suggestTrack() { for (var i = 0; i < fileCount; i++) $("input[name='track" + i + "']").val($("input[name='suggestedTrack" + i + "']").val()); }
    function resetTrack() { for (var i = 0; i < fileCount; i++) $("input[name='track" + i + "']").val($("input[name='originalTrack" + i + "']").val()); }
    function updateTags() {
        if (!fileCount) return;
        document.getElementById("save").disabled = true;
        index = 0;
        document.getElementById("errors").textContent = "";
        for (var i = 0; i < fileCount; i++) document.getElementById("status" + i).textContent = "";
        updateNextTag();
    }
    function updateNextTag() {
        var value = function(name) { return $("input[name='" + name + index + "']").val(); };
        document.getElementById("status" + index).textContent = editorMessages.working;
        tagEditApi.edit({id: value("id"), artist: value("artist"), track: value("track"), album: value("album"),
            title: value("title"), year: value("year"), genre: value("genre")})
            .then(setTagsCallback).catch(function(error) { setTagsCallback(error.message || "Tag editing failed"); });
    }
    function setTagsCallback(result) {
        var status = document.getElementById("status" + index);
        if (result === "SKIPPED") status.textContent = editorMessages.skipped;
        else if (result === "UPDATED") status.textContent = editorMessages.updated;
        else {
            status.textContent = editorMessages.error;
            var detail = document.createElement("div");
            detail.textContent = String(result);
            document.getElementById("errors").appendChild(detail);
        }
        index++;
        if (index < fileCount) updateNextTag();
        else document.getElementById("save").disabled = false;
    }
    function init() {
        var params = new URLSearchParams(window.location.search);
        var id = params.get("id");
        document.getElementById("backLink").href = "<c:url value='/main.view'/>?id=" + encodeURIComponent(id || "");
        if (!id || !/^\d+$/.test(id)) { document.getElementById("errors").textContent = editorMessages.loadError; return; }
        fetch(tagEditorDataUrl + "?id=" + encodeURIComponent(id), {credentials: "same-origin", headers: {"Accept": "application/json"}})
            .then(function(response) { if (!response.ok) throw new Error("Tag editor data request failed (" + response.status + ")"); return response.json(); })
            .then(function(data) {
                document.getElementById("artistAll").value = data.defaultArtist || "";
                document.getElementById("albumAll").value = data.defaultAlbum || "";
                document.getElementById("yearAll").value = data.defaultYear == null ? "" : data.defaultYear;
                var genreSelect = document.getElementById("genreAll");
                (data.allGenres || []).forEach(function(genre) {
                    var option = document.createElement("option"); option.value = genre; option.textContent = genre;
                    option.selected = genre === data.defaultGenre; genreSelect.appendChild(option);
                });
                (data.songs || []).forEach(addSong);
                fileCount = (data.songs || []).length;
            }).catch(function(error) { document.getElementById("errors").textContent = editorMessages.loadError + ": " + error.message; });
        $("input[name='artistAll'], input[name='albumAll'], input[name='yearAll']").keypress(function(event) {
            if (event.which === 13) { setArtist(); setAlbum(); setYear(); event.preventDefault(); }
        });
    }
</script>
</head>
<body class="mainframe bgcolor1" onload="init()">
<h1><fmt:message key="edittags.title"/></h1>
<div class="back"><a id="backLink" href="#"><fmt:message key="common.back"/></a></div>

<table class="ruleTable indent">
    <tr>
        <th class="ruleTableHeader"><fmt:message key="edittags.file"/></th>
        <th class="ruleTableHeader"><fmt:message key="edittags.track"/></th>
        <th class="ruleTableHeader"><fmt:message key="edittags.songtitle"/></th>
        <th class="ruleTableHeader"><fmt:message key="edittags.artist"/></th>
        <th class="ruleTableHeader"><fmt:message key="edittags.album"/></th>
        <th class="ruleTableHeader"><fmt:message key="edittags.year"/></th>
        <th class="ruleTableHeader"><fmt:message key="edittags.genre"/></th>
        <th class="ruleTableHeader" width="60pt"><fmt:message key="edittags.status"/></th>
    </tr>
    <tr>
        <th class="ruleTableHeader"/>
        <th class="ruleTableHeader"><a href="javascript:suggestTrack()"><fmt:message key="edittags.suggest.short"/></a> | <a href="javascript:resetTrack()"><fmt:message key="edittags.reset.short"/></a></th>
        <th class="ruleTableHeader"><a href="javascript:suggestTitle()"><fmt:message key="edittags.suggest"/></a> | <a href="javascript:resetTitle()"><fmt:message key="edittags.reset"/></a></th>
        <th class="ruleTableHeader" style="white-space: nowrap"><input id="artistAll" type="text" name="artistAll" size="15"/>&nbsp;<a href="javascript:setArtist()"><fmt:message key="edittags.set"/></a></th>
        <th class="ruleTableHeader" style="white-space: nowrap"><input id="albumAll" type="text" name="albumAll" size="15"/>&nbsp;<a href="javascript:setAlbum()"><fmt:message key="edittags.set"/></a></th>
        <th class="ruleTableHeader" style="white-space: nowrap"><input id="yearAll" type="text" name="yearAll" size="5"/>&nbsp;<a href="javascript:setYear()"><fmt:message key="edittags.set"/></a></th>
        <th class="ruleTableHeader" style="white-space: nowrap"><select id="genreAll" name="genreAll" style="width:7em"><option value=""/></select> <a href="javascript:setGenre()"><fmt:message key="edittags.set"/></a></th>
        <th class="ruleTableHeader"/>
    </tr>
    <tbody id="songs"></tbody>
</table>

<p><input type="button" id="save" value="<fmt:message key='common.save'/>" onclick="updateTags()"/></p>
<div class="warning" id="errors"/>
</body></html>
