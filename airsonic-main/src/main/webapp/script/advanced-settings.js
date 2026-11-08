function initAdvancedSettings(options) {
    var form = document.getElementById('advancedSettings');
    var fields = document.getElementById('advancedFields');
    var errorBox = document.getElementById('advancedSettingsError');
    var saved = document.getElementById('advancedSettingsSaved');
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
        var data = (await request(options.readUrl)).advancedSettings;
        form.querySelectorAll('[name]').forEach(function (input) {
            if (input.type === 'checkbox') input.checked = data[input.name];
            else input.value = data[input.name] == null ? '' : data[input.name];
        });
        document.getElementById('advancedBrand').textContent = data.brand;
        enableLdapFields();
    }
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        form.querySelectorAll('input[type=checkbox]').forEach(function (input) { body.set(input.name, String(input.checked)); });
        errorBox.textContent = ''; saved.hidden = true; fields.disabled = true;
        try {
            await request(options.saveUrl, body);
            await load(); saved.hidden = false;
        } catch (error) { errorBox.textContent = error.message; }
        finally { fields.disabled = false; }
    });
    load().then(function () { fields.disabled = false; }).catch(function (error) { errorBox.textContent = error.message; });
}
