function initPlayerSettings(options) {
    var form = document.getElementById('playerSettings');
    var fields = document.getElementById('playerFields');
    var selector = document.getElementById('playerSelector');
    var errorBox = document.getElementById('playerSettingsError');
    var saved = document.getElementById('playerSettingsSaved');
    var selectedId = new URLSearchParams(location.search).get('id');
    async function request(url, body) {
        var config = {credentials: 'same-origin'};
        if (body) {
            body.set('f', 'json'); body.set('c', 'airsonic-web');
            config.method = 'POST'; config.body = body; config.headers = {};
            config.headers[options.csrfHeader] = options.csrfToken;
        } else url += '?f=json&c=airsonic-web' + (selectedId ? '&id=' + encodeURIComponent(selectedId) : '');
        var response = await fetch(url, config);
        if (!response.ok || response.redirected) throw new Error('Request failed');
        var result = (await response.json())['subsonic-response'];
        if (!result || result.status !== 'ok') throw new Error(result && result.error ? result.error.message : 'Request failed');
        return result.playerSettings;
    }
    function render(data) {
        selector.replaceChildren();
        (data.player || []).forEach(function (player) { selector.add(new Option(player.description, player.id)); });
        var current = data.selectedPlayer;
        form.hidden = !current;
        document.getElementById('noPlayers').hidden = !!current;
        selectedId = current ? String(current.id) : null;
        var url = new URL(location.href);
        if (selectedId) url.searchParams.set('id', selectedId); else url.searchParams.delete('id');
        // Old GET mutation links never trigger writes.
        url.searchParams.delete('clone'); url.searchParams.delete('delete');
        history.replaceState(null, '', url);
        if (!current) return;
        selector.value = selectedId;
        var schemes = form.elements.transcodeSchemeName;
        schemes.replaceChildren();
        (data.transcodeScheme || []).forEach(function (scheme) { schemes.add(new Option(scheme.description, scheme.name)); });
        ['id', 'name', 'technologyName', 'transcodeSchemeName'].forEach(function (key) { form.elements[key].value = current[key]; });
        ['dynamicIp', 'autoControlEnabled', 'm3uBomEnabled'].forEach(function (key) { form.elements[key].checked = current[key]; });
        document.getElementById('playerType').textContent = current.type || '?';
        document.getElementById('playerLastSeen').textContent = current.lastSeen ? new Date(current.lastSeen).toLocaleString() : '';
        document.getElementById('noTranscoder').hidden = data.transcodingSupported;
        var profiles = document.getElementById('playerTranscodings'); profiles.replaceChildren();
        (data.transcoding || []).forEach(function (profile) {
            var label = document.createElement('label');
            var input = document.createElement('input'); input.type = 'checkbox'; input.name = 'activeTranscodingId'; input.value = profile.id;
            input.checked = (current.activeTranscodingId || []).indexOf(profile.id) !== -1;
            label.append(input, document.createTextNode(profile.name)); profiles.append(label, document.createTextNode(' '));
        });
        hideAllTechnologyDepends(); showTechnologyDepends(current.technologyName);
    }
    async function perform(url, body, showSaved) {
        errorBox.textContent = ''; saved.hidden = true; fields.disabled = true;
        try { render(await request(url, body)); saved.hidden = !showSaved; }
        catch (error) { errorBox.textContent = error.message; }
        finally { fields.disabled = false; }
    }
    selector.addEventListener('change', function () { selectedId = selector.value; perform(options.readUrl); });
    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        ['dynamicIp', 'autoControlEnabled', 'm3uBomEnabled'].forEach(function (key) { body.set(key, String(form.elements[key].checked)); });
        if (!body.has('activeTranscodingId')) body.set('activeTranscodingId', '');
        perform(options.saveUrl, body, true);
    });
    ['clone', 'delete'].forEach(function (action) {
        document.getElementById(action + 'Player').addEventListener('click', function () {
            perform(options[action + 'Url'], new URLSearchParams({id: selectedId}));
        });
    });
    perform(options.readUrl);
}
