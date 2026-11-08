function initMusicFolderSettings(options) {
    var form = document.getElementById('folderSettings');
    var fields = document.getElementById('folderFields');
    var errorBox = document.getElementById('folderSettingsError');
    var booleans = ['fastCache', 'ignoreSymLinks', 'fullScan', 'clearFullScanSettingAfterScan'];
    var scanTimer;
    function updateClearFullScan() {
        var clear = form.elements.clearFullScanSettingAfterScan;
        clear.disabled = !form.elements.fullScan.checked;
        if (clear.disabled) clear.checked = false;
    }
    async function request(url, body) {
        var headers = {}, params = new URLSearchParams({f: 'json', c: 'airsonic-web'});
        var config = {credentials: 'same-origin'};
        if (body) {
            body.set('f', 'json'); body.set('c', 'airsonic-web');
            headers[options.csrfHeader] = options.csrfToken;
            config.method = 'POST'; config.headers = headers; config.body = body;
        } else url += '?' + params;
        var response = await fetch(url, config);
        if (!response.ok || response.redirected) throw new Error('Request failed');
        var result = (await response.json())['subsonic-response'];
        if (!result || result.status !== 'ok') throw new Error(result && result.error ? result.error.message : 'Request failed');
        return result;
    }
    function scanStatus(scanning) {
        clearTimeout(scanTimer);
        document.getElementById('scanning').hidden = !scanning;
        document.getElementById('scanNow').disabled = scanning;
        if (scanning) scanTimer = setTimeout(async function () {
            try { scanStatus((await request(options.statusUrl)).scanStatus.scanning); }
            catch (error) { errorBox.textContent = error.message; }
        }, 5000);
    }
    function input(cell, name, value, type) {
        var field = document.createElement('input');
        field.name = name; field.type = type || 'text';
        if (type === 'checkbox') { field.checked = value; field.className = 'checkbox'; }
        else { field.value = value; field.style.width = '90%'; }
        cell.appendChild(field);
        return field;
    }
    function folders(container, list, editable) {
        var tbody = document.getElementById(container);
        tbody.replaceChildren();
        list.forEach(function (folder) {
            var row = tbody.insertRow(), prefix = 'folder.' + folder.id + '.';
            if (editable) {
                input(row.insertCell(), prefix + 'name', folder.name);
                input(row.insertCell(), prefix + 'path', folder.path);
                var typeCell = row.insertCell(); typeCell.textContent = folder.type;
                input(typeCell, 'folderId', folder.id, 'hidden');
                input(typeCell, prefix + 'type', folder.type, 'hidden');
                input(row.insertCell(), prefix + 'enabled', folder.enabled, 'checkbox');
                input(row.insertCell(), prefix + 'delete', false, 'checkbox');
            } else {
                row.insertCell().textContent = folder.name;
                row.insertCell().textContent = folder.path;
                row.insertCell().textContent = folder.type;
                row.insertCell(); row.insertCell();
            }
            var status = row.insertCell();
            if (!folder.existing) {
                var warning = document.createElement('span'); warning.className = 'warning';
                warning.textContent = document.getElementById('folderNotFound').textContent; status.appendChild(warning);
            }
            if (folder.overlap) {
                var overlap = document.createElement('span');
                overlap.textContent = ' ' + document.getElementById('folderOverlap').textContent + ' ' + folder.overlapStatus;
                status.appendChild(overlap);
            }
        });
    }
    async function load() {
        var data = (await request(options.readUrl)).musicFolderSettings;
        ['interval', 'hour', 'uploadsFolder', 'excludePatternString'].forEach(function (name) { form.elements[name].value = data[name]; });
        booleans.forEach(function (name) { form.elements[name].checked = data[name]; });
        folders('musicFolders', (data.folders || {}).folder || [], true);
        folders('deletedMusicFolders', (data.deletedFolders || {}).folder || [], false);
        var types = form.elements['newMusicFolder.type']; types.replaceChildren();
        ((data.types || {}).type || []).forEach(function (type) { types.add(new Option(type, type)); });
        form.elements['newMusicFolder.path'].value = ''; form.elements['newMusicFolder.name'].value = '';
        form.elements['newMusicFolder.enabled'].checked = true;
        fields.disabled = false; updateClearFullScan(); scanStatus(data.scanning);
    }
    async function write(url, body) {
        errorBox.textContent = ''; fields.disabled = true;
        document.getElementById('folderSettingsSaved').hidden = true;
        try {
            await request(url, body);
            await load();
            document.getElementById('folderSettingsSaved').hidden = url !== options.saveUrl;
            if (parent.frames.left) parent.frames.left.location.reload();
        } catch (error) { errorBox.textContent = error.message; }
        finally { fields.disabled = false; updateClearFullScan(); }
    }
    form.elements.fullScan.addEventListener('change', updateClearFullScan);
    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        form.querySelectorAll('input[type=checkbox]').forEach(function (field) { body.set(field.name, String(field.checked)); });
        write(options.saveUrl, body);
    });
    document.getElementById('scanNow').addEventListener('click', function () { write(options.scanUrl, new URLSearchParams()); });
    document.getElementById('expunge').addEventListener('click', function () { write(options.expungeUrl, new URLSearchParams()); });
    load().catch(function (error) { errorBox.textContent = error.message; });
    window.addEventListener('pagehide', function () { clearTimeout(scanTimer); });
}
