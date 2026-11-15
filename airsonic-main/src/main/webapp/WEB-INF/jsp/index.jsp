<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html><head>
    <%@ include file="head.jsp" %>
    <style>
        iframe {
            border: none;
        }
        .entire-panel {
            display: flex;
            flex-direction: column;
            height: 100vh;
        }
        .main-navigation {
            width: 100%;
        }
        .lower {
            flex: 100%;
            display: flex;
            flex-direction: row;
            height: 100%;
        }
        div.left-nav-container {
            width: 16%;
            min-width: 16%;
        }
        .left-navigation {
            width: 100%;
            height: 100%;
        }
        div.non-left-navigation-container {
            flex: 1;
            display: flex;
            flex-direction: column;
            height: 100%;
            width: 84%;
        }
        .main-right-container {
            flex: 1;
            display: flex;
            flex-direction: row;
        }
        .playqueue-container {
            padding-left: 1em;
            padding-right: 1em;
        }
        .main-panel {
            flex: 80%;
        }
        .right-info-container {
            flex: 20%;
        }
        .right-info {
            width: 100%;
            height: 100%;
        }
        .hidden-panel {
            display: none;
        }
    </style>
</head>

<body class="bgcolor2" style="height: 100%; margin: 0; overflow-y: hidden" data-main="<c:out value='${param.main}'/>">
    <script src="<c:url value='/script/index-page.js'/>?v=1"></script>
    <script type="text/javascript">
        function startWebsite(settings) {
            window.keyboardShortcutsEnabled = settings.keyboardShortcutsEnabled === true;
            document.querySelector('.left-nav-container').style.display = settings.showSideBar ? '' : 'none';
            document.querySelector('.right-info-container').style.display = settings.showRight ? '' : 'none';

            var target = resolveIndexMainTarget(document.body.getAttribute('data-main'), "<c:url value='/'/>", window.location.href);

            document.getElementById('upper').src = "<c:url value='/top.view?'/>";
            document.getElementById('left').src = "<c:url value='/left.view?'/>";
            document.getElementById('right').src = "<c:url value='/right.view?'/>";
            document.getElementById('main').src = target;
        }

        fetch("<c:url value='/rest/indexPageSettings'/>", {credentials: 'same-origin', cache: 'no-store'})
            .then(function (response) {
                if (!response.ok || response.redirected) throw new Error('Website settings request failed');
                return response.json();
            }).then(function (settings) {
                if (typeof settings.showSideBar !== 'boolean' || typeof settings.showRight !== 'boolean' ||
                        typeof settings.keyboardShortcutsEnabled !== 'boolean') {
                    throw new Error('Invalid website settings response');
                }
                startWebsite(settings);
            }).catch(function (error) {
                console.error(error);
                startWebsite({showSideBar: true, showRight: true, keyboardShortcutsEnabled: false});
            });
    </script>
    <div class="entire-panel">
        <iframe id="upper" name="upper" onload="this.style.height=(this.contentWindow.document.body.clientHeight+15)+'px';" class="bgcolor2 main-navigation"></iframe>

        <div class="lower">
            <div class="bgcolor2 left-nav-container">
                <iframe id="left" name="left" class="left-navigation"></iframe>
            </div>
            <div class="non-left-navigation-container">
                <div class="main-right-container">
                    <iframe id="main" name="main" class="bgcolor1 main-panel" allowfullscreen></iframe>
                    <div class="bgcolor1 right-info-container">
                        <iframe id="right" name="right" class="right-info"></iframe>
                    </div>
                </div>
                <div class="bgcolor2 playqueue-container">
                    <jsp:include page="playQueue.jsp" />
                </div>
           </div>
       </div>

       <iframe id="hidden" name="hidden" class="hidden-panel"></iframe>
   </div>
</body>
</html>
