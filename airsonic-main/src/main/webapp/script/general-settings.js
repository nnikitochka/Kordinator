function initGeneralSettings(options) {
    var form = document.getElementById('generalSettings');
    var fields = document.getElementById('generalFields');
    var errorBox = document.getElementById('generalSettingsError');
    var saved = document.getElementById('generalSettingsSaved');
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
        return result;
    }
    async function load() {
        var data = (await request(options.readUrl)).generalSettings;
        [['themeId', (data.themes || {}).theme], ['locale', (data.locales || {}).locale]].forEach(function (entry) {
            var select = form.elements[entry[0]]; select.replaceChildren();
            (entry[1] || []).forEach(function (option) { select.add(new Option(option.name, option.id)); });
        });
        form.querySelectorAll('[name]').forEach(function (input) {
            if (input.type === 'checkbox') input.checked = data[input.name];
            else input.value = data[input.name] == null ? '' : data[input.name];
        });
        fields.disabled = false;
    }
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        form.querySelectorAll('input[type=checkbox]').forEach(function (input) { body.set(input.name, String(input.checked)); });
        errorBox.textContent = ''; saved.hidden = true; fields.disabled = true;
        try {
            await request(options.saveUrl, body);
            window.location.replace(options.pageUrl + "?saved=true");
        } catch (error) { errorBox.textContent = error.message; }
        finally { fields.disabled = false; }
    });
    load().catch(function (error) { errorBox.textContent = error.message; });
}
