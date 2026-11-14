(function () {
    'use strict';

    function option(select, value, label) {
        var item = document.createElement('option');
        item.value = value;
        item.textContent = label;
        select.appendChild(item);
    }

    document.addEventListener('DOMContentLoaded', function () {
        var config = document.getElementById('morePageData');
        if (!config) return;
        fetch(config.dataset.url, {credentials: 'same-origin', cache: 'no-store'})
            .then(function (response) {
                if (!response.ok) throw new Error('Unable to load page data');
                return response.json();
            })
            .then(function (data) {
                if (data.user && data.user.streamRole) {
                    var random = document.getElementById('randomQueueSection');
                    random.hidden = false;
                    var year = document.querySelector('#randomPlayQueue select[name="year"]');
                    for (var y = data.currentYear; y >= 2010; y--) option(year, y + ' ' + y, String(y));
                    var genres = document.querySelector('#randomPlayQueue select[name="genre"]');
                    (data.genres || []).forEach(function (genre) {
                        option(genres, genre.name, genre.name + ' (' + genre.songCount + ')');
                    });
                    var folders = document.querySelector('#randomPlayQueue select[name="musicFolderId"]');
                    (data.musicFolders || []).forEach(function (folder) { option(folders, folder.id, folder.name); });
                }
                if (data.user && data.user.uploadRole) {
                    document.getElementById('uploadSection').hidden = false;
                    document.getElementById('dir').value = data.uploadDirectory || '';
                    watchUploads(updateUploads);
                    bindFileUpload(document.getElementById('fileUpload'), document.getElementById('uploadResults'), {
                        url: document.getElementById('fileUpload').action,
                        csrfHeader: config.dataset.csrfHeader,
                        csrfToken: config.dataset.csrfToken,
                        uploaded: document.getElementById('uploadSuccessTemplate').textContent,
                        extracted: document.getElementById('uploadExtractedTemplate').textContent
                    });
                }
            })
            .catch(function (error) { console.error('More page data failed to load', error); });
    });
}());
