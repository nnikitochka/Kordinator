<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8"%>
<%--@elvariable id="model" type="java.util.Map"--%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <%@ include file="subsonicStars.jsp" %>
    <%@ include file="subsonicBookmarks.jsp" %>
    <%@ include file="table.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/utils.js'/>"></script>

    <script type="text/javascript" language="javascript">

        var bookmarks = {};
        var bookmarksTable;

        function init() {
        	var ratingOnImage = "<spring:theme code='ratingOnImage'/>";
            var ratingOffImage = "<spring:theme code='ratingOffImage'/>";

            bookmarksTable = $("#bookmarksTable").DataTable( {
                deferRender: true,
                createdRow(row, data, dataIndex, cells) {
                    var rowNode = $(row);
                    if (rowNode.hasClass("selected")) {
                        rowNode.find(".bookmarksIndex input").prop("checked", true);
                    }
                },
                colReorder: true,
                fixedHeader: true,
                stateSave: true,
                stateDuration: 60 * 60 * 24 * 365,
                ordering: true,
                order: [],
                orderMulti: true,
                pageLength: ${model.initialPaginationSize},
              <c:set var="paginationaddition" value="${fn:contains(' 10 20 50 100 -1', ' '.concat(model.initialPaginationSize)) ? '' : ', '.concat(model.initialPaginationSize)}" />
                lengthMenu: [[10, 20, 50, 100, -1 ${paginationaddition}], [10, 20, 50, 100, "All" ${paginationaddition}]],
                processing: true,
                autoWidth: true,
                scrollCollapse: true,
                select: {
                    style: "multi",
                    selector: ".bookmarksIndex"
                },
                language: {
                    emptyTable: "<fmt:message key='bookmarks.empty'/>"
                },
                ajax: function(ajaxData, callback) {
                    callback({data: Object.values(bookmarks)});
                },
                stripeClasses: ["bgcolor2", "bgcolor1"],
                columnDefs: [{ targets: "_all", orderable: true }],
                columns: [
                    { data: "id", className: "detail fit", visible: true },
                    { data: null,
                      searchable: false,
                      name: "bookmarkscheckbox",
                      className: "fit not-draggable bookmarksIndex centeralign",
                      title: "<input type='checkbox' class='bookmarksSelectAll'>",
                      defaultContent: "<input type='checkbox'>"
                    },
                    { data: "mediaFileEntry.starred",
                      name: "starred",
                      className: "fit not-draggable centeralign",
                      render: function(starred, type) {
                          if (type == "display") {
                              return "<img class='starSong' src='" + (starred ? ratingOnImage : ratingOffImage) + "' style='height:18px;' alt='' title=''>";
                          }
                          return starred ? "onlystarred" : "unstarred";
                      }
                    },
                    { data: "mediaFileEntry.present",
                      searchable: false,
                      name: "play",
                      className: "fit not-draggable centeralign",
                      render: function(present, type, row) {
                          if (type == "display") {
                              if (present) {
                                  return "<img class='playSong' src=\"<spring:theme code='playImage'/>\" style='height:18px;' alt=\"<fmt:message key='common.play'/>\" title=\"<fmt:message key='common.play'/>\">";
                              } else {
                                  return "";
                              }
                          }
                          return present ? "available" : "missing";
                      }
                    },
                    { data: "mediaFileEntry.present",
                      searchable: false,
                      name: "addLast",
                      className: "fit not-draggable centeralign",
                      render: function(present, type, row) {
                          if (type == "display") {
                              if (present) {
                                  return "<img class='addSongLast' src=\"<spring:theme code='addImage'/>\" style='height:18px;' alt=\"<fmt:message key='common.add'/>\" title=\"<fmt:message key='common.add'/>\">";
                              } else {
                                  return "";
                              }
                          }
                          return present ? "available" : "missing";
                      }
                    },
                    { data: "mediaFileEntry.present",
                      searchable: false,
                      name: "addNext",
                      className: "fit not-draggable centeralign",
                      render: function(present, type, row) {
                          if (type == "display") {
                              if (present) {
                                  return "<img class='addSongNext' src=\"<spring:theme code='addNextImage'/>\" style='height:18px;' alt=\"<fmt:message key='main.addnext'/>\" title=\"<fmt:message key='main.addnext'/>\">";
                              } else {
                                  return "";
                              }
                          }
                          return present ? "available" : "missing";
                      }
                    },
                    { data: "mediaFileEntry.title",
                      className: "detail songTitle truncate",
                      title: "<fmt:message key='edittags.songtitle'/>",
                      render: function(title, type, row) {
                          if (type == "display" && title != null) {
                              return $("<span>", {title: title, alt: title, text: title})[0].outerHTML;
                          }
                          return title;
                      }
                    },
                    { data: "mediaFileEntry.album",
                      className: "detail truncate",
                      title: "<fmt:message key='personalsettings.album'/>",
                      render: function(album, type, row) {
                          if (type == "display" && album != null) {
                              return $("<a>", {title: album, alt: album, text: album, target: "main"}).attr("href", "main.view?id=" + row.mediaFileEntry.parent)[0].outerHTML;
                          }
                          return album;
                      }
                    },
                    { data: "mediaFileEntry.artist",
                      className: "detail truncate",
                      title: "<fmt:message key='personalsettings.artist'/>",
                      render: function(artist, type, row) {
                          if (type == "display" && artist != null) {
                              return $("<span>", {title: artist, alt: artist, text: artist})[0].outerHTML;
                          }
                          return artist;
                      }
                    },
                    { data: "comment",
                      className: "detail truncate",
                      title: "<fmt:message key='sharesettings.description'/>",
                      render(comment, type) {
                          if (type == "display" && comment != null) {
                              return $("<span>", {title: comment, alt: comment, text: comment})[0].outerHTML;
                          }
                          return comment;
                      }
                    },
                    { data: "positionMillis",
                      className: "detail fit rightalign",
                      title: "<fmt:message key='bookmarks.position'/>",
                      render: function(data, type, row) {
                          if (type == "display" && data != null) {
                              return formatDuration(Math.round(data/1000)) + " / " + formatDuration(Math.round(row.mediaFileEntry.duration));
                          }
                          return data;
                      }
                    },
	                { data: "created",
	                  className: "detail fit rightalign",
	                  title: "<fmt:message key='personalsettings.created'/>",
	                  render: function(data, type, row) {
	                      if (data != null) {
	                          if (type == "display") {
	                              return new Date(data).toLocaleString();
	                          }
	                          return new Date(data).getTime();
	                      }
	                      return data;
	                  }
	                },
	                { data: "changed",
	                  className: "detail fit rightalign",
	                  title: "<fmt:message key='personalsettings.changed'/>",
	                  render: function(data, type, row) {
	                      if (data != null) {
	                          if (type == "display") {
	                              return new Date(data).toLocaleString();
	                          }
	                          return new Date(data).getTime();
	                      }
	                      return data;
	                  }
	                },
                    { data: null,
                      searchable: false,
                      name: "remove",
                      className: "fit not-draggable centeralign",
                      defaultContent: "<img class='removeBookmark' src=\"<spring:theme code='removeImage'/>\" style='height:18px;' alt=\"<fmt:message key='playlist.remove'/>\" title=\"<fmt:message key='playlist.remove'/>\">"
                    }
                ]
            } );

            bookmarksTable.on( 'select', function ( e, dt, type, indexes ) {
                bookmarksTable.cells( indexes, "bookmarkscheckbox:name" ).nodes().to$().find("input").prop("checked", true);
                updateSelectAllCheckboxStatus();
            } );
            bookmarksTable.on( 'deselect', function ( e, dt, type, indexes ) {
                bookmarksTable.cells( indexes, "bookmarkscheckbox:name" ).nodes().to$().find("input").prop("checked", false);
                updateSelectAllCheckboxStatus();
            } );
            $("#bookmarksTable tbody").on( "click", ".starSong", function () {
                onStar(bookmarksTable.row( $(this).parents('tr') ).index());
            } );
            $("#bookmarksTable tbody").on( "click", ".playSong", function () {
                onPlay(bookmarksTable.row( $(this).parents('tr') ).index());
            } );
            $("#bookmarksTable tbody").on( "click", ".addSongLast", function () {
                onAdd(bookmarksTable.row( $(this).parents('tr') ).index());
            } );
            $("#bookmarksTable tbody").on( "click", ".addSongNext", function () {
                onAddNext(bookmarksTable.row( $(this).parents('tr') ).index());
            } );
            $("#bookmarksTable tbody").on( "click", ".removeBookmark", function () {
                onRemoveBookmark(bookmarksTable.row( $(this).parents('tr') ).index());
            } );
            $(".bookmarksSelectAll").on( "change", function (e) {
                selectAll(e.target.checked);
            } );

            bookmarkApi.watch(top.StompClient, "bookmarks.jsp", getBookmarksCallback);
        }

        function onAdd(index) {
            top.playQueue.onAdd(bookmarksTable.row(index).data().mediaFileEntry.id);
            $().toastmessage('showSuccessToast', '<fmt:message key="main.addlast.toast"/>');
        }
        function onAddNext(index) {
            top.playQueue.onAddNext(bookmarksTable.row(index).data().mediaFileEntry.id);
            $().toastmessage('showSuccessToast', '<fmt:message key="main.addnext.toast"/>');
        }
        function onStar(index) {
            var bookmark = bookmarksTable.row(index).data();
            var starred = !bookmark.mediaFileEntry.starred;
            setMediaFilesStarred([bookmark.mediaFileEntry.id], starred, function () {
                bookmark.mediaFileEntry.starred = starred;
                bookmarksTable.rows().invalidate();
            });
        }
        function onRemoveSelectedBookmarks() {
            var indices = bookmarksTable.rows({ selected: true }).indexes().toArray();
            for (let i of indices) {
              onRemoveBookmark(i);
            }
        }
        function onRemoveBookmark(index) {
            bookmarkApi.remove(bookmarksTable.row(index).data().mediaFileEntry.id, function () {
                bookmarkApi.list(getBookmarksCallback);
            });
        }
        function getBookmarksCallback(bookmarks) {
            this.bookmarks = bookmarks;
            bookmarksTable.ajax.reload().columns.adjust();
        }

        <!-- actionSelected() is invoked when the users selects from the "More actions..." combo box. -->
        function actionSelected(id) {
          if (id == "top") {
              return;
          } else if (id == "removeSelected") {
              this.onRemoveSelectedBookmarks();
          }
        }

        function selectAll(b) {
            if (b) {
                bookmarksTable.rows().select();
            } else {
                bookmarksTable.rows().deselect();
            }
        }

        function updateSelectAllCheckboxStatus() {
            if (bookmarksTable.rows({selected: true}).indexes().length == 0) {
                $('.bookmarksSelectAll').prop('checked', false);
                $('.bookmarksSelectAll').prop('indeterminate', false);
            } else if (bookmarksTable.rows({selected: true}).indexes().length == bookmarksTable.rows().indexes().length) {
                $('.bookmarksSelectAll').prop('checked', true);
                $('.bookmarksSelectAll').prop('indeterminate', false);
            } else {
                $('.bookmarksSelectAll').prop('indeterminate', true);
            }
        }
    </script>
</head>

<body class="mainframe bgcolor1" onload="init()">

<h1 style="padding-bottom: 1em">
    <img src="<spring:theme code='bookmarkImage'/>" alt="">
    <span style="vertical-align: middle"><fmt:message key="top.bookmarks"/></span>
</h1>

<table class="music indent hover nowrap stripe compact" id="bookmarksTable" style="cursor: pointer; width: 100%; margin-top: 5px;">
</table>

<div id="moreactions" style="white-space:nowrap;">
    <span class="header">
        <select id="moreActions" onchange="actionSelected(options[selectedIndex].id)">
            <option id="top" selected="selected"><fmt:message key="playlist.more"/></option>
            <optgroup label="<fmt:message key='bookmarks.more.selection'/>">
                <option id="removeSelected"><fmt:message key="playlist.remove"/></option>
            </optgroup>
        </select>
    </span>
</div>

<p style="width:60%">
    <fmt:message key="bookmarks.info"/>
</p>

</body>
</html>
