function initPersonalSettings(options) {
    var form = document.getElementById('personalSettings');
    var fields = document.getElementById('personalFields');
    var errorBox = document.getElementById('personalSettingsError');
    var saved = document.getElementById('personalSettingsSaved');
    var username;
    var uploadForm = document.getElementById('avatarUpload');
    async function request(url, body) {
        var config = {credentials: 'same-origin'};
        if (body) {
            body.set('f', 'json'); body.set('c', 'airsonic-web');
            config.method = 'POST'; config.body = body; config.headers = {};
            config.headers[options.csrfHeader] = options.csrfToken;
        } else url += '?f=json&c=airsonic-web';
        var response = await fetch(url, config);
        if (!response.ok || response.redirected) throw new Error('Request failed');
        var result = (await response.json())['subsonic-response'];
        if (!result || result.status !== 'ok') throw new Error(result && result.error ? result.error.message : 'Request failed');
        return result.personalSettings;
    }
    function image(avatar, query) {
        var img = document.createElement('img');
        img.src = options.avatarUrl + '?c=airsonic-web&' + query;
        img.alt = avatar.name; img.width = avatar.width; img.height = avatar.height;
        img.style.paddingRight = '2em';
        return img;
    }
    async function load() {
        var data = await request(options.readUrl);
        username = data.username;
        [['themeId', data.theme], ['locale', data.localeOption]].forEach(function (entry) {
            var select = form.elements[entry[0]];
            while (select.options.length > 1) select.remove(1);
            (entry[1] || []).forEach(function (item) { select.add(new Option(item.name, item.id)); });
        });
        var avatars = document.getElementById('personalAvatars'); avatars.replaceChildren();
        (data.avatar || []).forEach(function (avatar) {
            var label = document.createElement('label'); label.style.whiteSpace = 'nowrap';
            var input = document.createElement('input'); input.type = 'radio'; input.name = 'avatarId'; input.value = avatar.id;
            label.append(input, image(avatar, 'id=' + encodeURIComponent(avatar.id))); avatars.append(label);
        });
        var custom = document.getElementById('personalCustomAvatar'); custom.replaceChildren();
        if (data.customAvatar) custom.append(image(data.customAvatar, 'username=' + encodeURIComponent(data.username) + '&forceCustom=true'));
        form.querySelectorAll('[name]').forEach(function (input) {
            var value = input.name.split('.').reduce(function (object, key) { return object == null ? undefined : object[key]; }, data);
            if (input.type === 'checkbox') input.checked = value;
            else if (input.type === 'radio') input.checked = String(value) === input.value;
            else input.value = value == null ? '' : value;
        });
        lastfmCredsAbsent = data.lastfmCredsAbsent;
        listenBrainzCredsAbsent = data.listenBrainzCredsAbsent;
        enableFields(); fields.disabled = false;
        document.getElementById('avatarUploadFields').disabled = false;
    }
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        form.querySelectorAll('input[type=checkbox]').forEach(function (input) { body.set(input.name, String(input.checked)); });
        errorBox.textContent = ''; saved.hidden = true; fields.disabled = true;
        try {
            await request(options.saveUrl, body);
            if (window.parent !== window) {
                window.parent.location.href = options.indexUrl + '?main=' + encodeURIComponent('personalSettings.view?saved=true');
            } else window.location.replace(options.pageUrl + '?saved=true');
        } catch (error) { errorBox.textContent = error.message; fields.disabled = false; }
    });
    uploadForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        var error = document.getElementById('avatarUploadError');
        var success = document.getElementById('avatarUploadSaved');
        var button = uploadForm.querySelector('input[type=submit]');
        error.textContent = ''; success.hidden = true;
        var file = uploadForm.elements.file.files[0];
        if (!file) { error.textContent = 'Missing file'; return; }
        var body = new FormData(); body.append('file', file);
        var headers = {}; headers[options.csrfHeader] = options.csrfToken;
        button.disabled = true;
        try {
            var response = await fetch(options.uploadUrl + '?f=json&c=airsonic-web', {
                method: 'POST', body: body, credentials: 'same-origin', headers: headers
            });
            if (!response.ok || response.redirected) throw new Error('Request failed');
            var result = (await response.json())['subsonic-response'];
            if (!result || result.status !== 'ok') throw new Error(result && result.error ? result.error.message : 'Request failed');
            var preview = document.getElementById('personalCustomAvatar');
            preview.replaceChildren(image(result.avatarUpload.avatar,
                'username=' + encodeURIComponent(username) + '&forceCustom=true&_=' + Date.now()));
            uploadForm.reset(); success.hidden = false;
        } catch (failure) { error.textContent = failure.message; }
        finally { button.disabled = false; }
    });
    load().catch(function (error) { errorBox.textContent = error.message; });
}
