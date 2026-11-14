(function () {
    'use strict';
    function element(tag, text, className) {
        var node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    }
    function link(parent, text, href, target) {
        var a = element('a', text); a.href = href;
        if (target) a.target = target;
        parent.appendChild(a); return a;
    }
    function loadLeftPage(config) {
        if (!config) return;
        var url = new URL(config.dataset.url, location.href);
        if (config.dataset.refresh === 'true') url.searchParams.set('refresh', 'true');
        fetch(url, {credentials: 'same-origin', cache: 'no-store'}).then(function (response) {
            if (!response.ok) throw new Error('Unable to load library navigation');
            return response.json();
        }).then(function (data) {
            var picker = document.getElementById('leftFolderPicker');
            if ((data.folders || []).length > 1) {
                picker.hidden = false;
                var select = document.getElementById('leftMusicFolder');
                var all = element('option', config.dataset.allFoldersLabel); all.value = '-1';
                select.appendChild(all);
                (data.folders || []).forEach(function (folder) {
                    var option = element('option', folder.name); option.value = folder.id;
                    if (folder.id === data.selectedFolderId) option.selected = true;
                    select.appendChild(option);
                });
                select.addEventListener('change', function () {
                    var body = new URLSearchParams({musicFolderId: select.value});
                    fetch(config.dataset.selectUrl, {method: 'POST', credentials: 'same-origin',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded', [config.dataset.csrfHeader]: config.dataset.csrfToken}, body: body
                    }).then(function (r) {
                        if (!r.ok) throw new Error('Folder selection failed');
                        return r.json();
                    }).then(function (result) {
                        if (result.changed && top.main.location.href.indexOf('/home.view') !== -1) top.main.location.reload();
                        location.reload();
                    }).catch(function (error) { console.error('Folder selection failed', error); });
                });
            }
            var indexes = data.indexes || [];
            function addIndexNav(container) { indexes.forEach(function (group) { link(container, group.index, '#' + group.index); container.appendChild(document.createTextNode(' ')); }); }
            addIndexNav(document.getElementById('leftIndexes')); addIndexNav(document.getElementById('leftIndexFooter'));
            var refresh = document.getElementById('leftRefresh'); refresh.href = data.scanning ? 'left.view' : 'left.view?refresh=true';
            var shortcutList = document.getElementById('leftShortcutItems');
            (data.shortcuts || []).forEach(function (item) { var p = element('p', undefined, 'dense truncate'); p.style.paddingLeft = '2px'; link(p, item.name, 'main.view?id=' + encodeURIComponent(item.id), 'main'); shortcutList.appendChild(p); });
            document.getElementById('leftShortcuts').hidden = !(data.shortcuts || []).length;
            var artistRoot = document.getElementById('leftArtists');
            indexes.forEach(function (group) {
                var table = element('table'); table.className = 'bgcolor1'; table.style.cssText = 'width:100%;padding:0;margin:1em 0;border:0';
                var row = table.insertRow(), head = row.insertCell(); head.style.textAlign = 'left';
                var anchor = element('a'); anchor.name = group.index; head.appendChild(anchor);
                var title = element('h2', group.index); title.style.margin = '0'; head.appendChild(title);
                var up = row.insertCell(); up.style.textAlign = 'right'; link(up, '↑', '#top'); artistRoot.appendChild(table);
                (group.artists || []).forEach(function (artist) {
                    var p = element('p', undefined, 'dense truncate'); p.style.paddingLeft = '2px';
                    var ids = (artist.ids || []).map(function (id) { return 'id=' + encodeURIComponent(id); }).join('&');
                    var a = link(p, artist.name.length > 35 ? artist.name.slice(0, 35) + '…' : artist.name, 'main.view?' + ids, 'main'); a.title = artist.name; artistRoot.appendChild(p);
                });
            });
            (data.songs || []).forEach(function (song) {
                var p = element('p', undefined, 'dense truncate'); p.style.paddingLeft = '2px';
                if (song.present && data.streamRole && !data.partyMode && (!song.video || data.playerWeb)) {
                    var play = element('img'); play.src = config.dataset.mainImage; play.alt = 'Play'; play.style.cssText = 'cursor:pointer;height:18px';
                    if (song.video) { var a = element('a'); a.href = 'videoPlayer.view?id=' + song.id; a.target = 'main'; a.appendChild(play); p.appendChild(a); }
                    else { play.onclick = function () { top.playQueue.onPlay(song.id); }; p.appendChild(play); }
                }
                if (song.present && data.streamRole && !song.video) {
                    var add = element('img'); add.src = config.dataset.addImage; add.alt = 'Add'; add.style.cssText = 'cursor:pointer;height:18px'; add.onclick = function () { top.playQueue.onAdd(song.id); }; p.appendChild(add);
                    var next = element('img'); next.src = config.dataset.addNextImage; next.alt = 'Add next'; next.style.cssText = 'cursor:pointer;height:18px'; next.onclick = function () { top.playQueue.onAddNext(song.id); }; p.appendChild(next);
                }
                if (song.present && data.downloadRole && !data.partyMode) {
                    var downloadLink = element('a'); downloadLink.href = config.dataset.downloadUrl + '?id=' + encodeURIComponent(song.id);
                    var download = element('img'); download.src = config.dataset.downloadImage; download.alt = 'Download'; download.style.height = '18px'; downloadLink.appendChild(download); p.appendChild(downloadLink);
                }
                var title = element('span', song.title, 'songTitle'); title.title = song.title; p.appendChild(title); document.getElementById('leftSongs').appendChild(p);
            });
            if (data.statistics && data.statistics.songCount > 0) {
                var stats = document.getElementById('leftStatistics');
                stats.textContent = stats.dataset.format.replace('{0}', data.statistics.artistCount).replace('{1}', data.statistics.albumCount).replace('{2}', data.statistics.songCount).replace('{3}', data.statistics.bytes).replace('{4}', data.statistics.hours); stats.hidden = false;
            }
        }).catch(function (error) { console.error('Left navigation failed', error); });
    }
    window.loadLeftPage = loadLeftPage;
}());
