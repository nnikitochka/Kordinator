function initPlayQueuePage(options) {
    fetch(options.url, {credentials: 'same-origin', cache: 'no-store'})
        .then(function (response) {
            if (!response.ok) throw new Error('Unable to load play queue settings');
            return response.json();
        })
        .then(function (settings) {
            if (!settings || !settings.user || !settings.visibility || !Array.isArray(settings.players)) {
                throw new Error('Invalid play queue settings');
            }
            options.queue.pageSettings = settings;
            var doc = options.document;
            if (settings.user.settingsRole && settings.players.length > 1) {
                doc.getElementById('playQueueSelectorContainer').style.display = '';
                settings.players.forEach(function (player) {
                    var option = doc.createElement('option');
                    option.value = player.id;
                    option.textContent = player.description;
                    doc.getElementById('playerSelector').appendChild(option);
                });
            }
            Object.keys(settings.user).forEach(function (role) {
                doc.querySelectorAll('[data-play-queue-role="' + role + '"]').forEach(function (node) {
                    node.hidden = !settings.user[role];
                    if (node.style) node.style.display = settings.user[role] ? '' : 'none';
                });
            });
            if (!settings.autoHide) doc.getElementById('playQueueToggle').style.display = '';
            options.queue.init();
        })
        .catch(function () {
            docOr(options.document, 'playQueuePageError').hidden = false;
        });
}

function docOr(document, id) { return document.getElementById(id); }
