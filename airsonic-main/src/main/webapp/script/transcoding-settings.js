function initTranscodingSettings(options) {
    var form = document.getElementById('transcodingSettings');
    var fields = document.getElementById('transcodingFields');
    var errorBox = document.getElementById('transcodingSettingsError');
    var saved = document.getElementById('transcodingSettingsSaved');
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
        var data = (await request(options.readUrl)).transcodingSettings;
        var rows = document.getElementById('transcodingRows'); rows.replaceChildren();
        (data.transcoding || []).forEach(function (profile) {
            var row = rows.insertRow();
            ['name', 'sourceFormats', 'targetFormat', 'step1', 'step2', 'delete'].forEach(function (name, index) {
                var input = document.createElement('input');
                input.name = name + '[' + profile.id + ']';
                input.className = 'monospace';
                if (name === 'delete') input.type = 'checkbox';
                else { input.size = [10, 36, 10, 60, 22][index]; input.value = profile[name] || ''; }
                row.insertCell().appendChild(input);
            });
        });
        ['downsampleCommand', 'hlsCommand', 'jukeboxCommand', 'videoImageCommand', 'subtitlesExtractionCommand',
            'transcodeEstimateTimePadding', 'transcodeEstimateBytePadding'].forEach(function (name) {
            form.elements[name].value = data[name] == null ? '' : data[name];
        });
        ['name', 'sourceFormats', 'targetFormat', 'step1', 'step2'].forEach(function (name) { form.elements[name].value = ''; });
        document.getElementById('transcodeDirectory').textContent = data.transcodeDirectory;
        document.getElementById('transcodingBrand').textContent = data.brand;
    }
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        form.querySelectorAll('input[type=checkbox]').forEach(function (input) { body.set(input.name, String(input.checked)); });
        fields.disabled = true; saved.hidden = true; errorBox.textContent = '';
        try { await request(options.saveUrl, body); await load(); saved.hidden = false; }
        catch (error) { errorBox.textContent = error.message; }
        finally { fields.disabled = false; }
    });
    load().then(function () { fields.disabled = false; }).catch(function (error) { errorBox.textContent = error.message; });
}
