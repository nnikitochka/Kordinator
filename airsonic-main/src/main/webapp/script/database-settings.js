function initDatabaseSettings(options) {
    var form = document.getElementById('databaseSettings');
    var fields = document.getElementById('databaseFields');
    var actions = document.getElementById('databaseActions');
    var errorBox = document.getElementById('databaseError');
    var saved = document.getElementById('databaseSaved');
    var operation = document.getElementById('databaseOperation');
    var importForm = document.getElementById('databaseImport');
    var busy = false;
    async function request(name, body, binary) {
        var config = {credentials: 'same-origin', cache: 'no-store'}, url = options.baseUrl + name;
        if (body) {
            config.method = 'POST'; config.body = body; config.headers = {};
            config.headers[options.csrfHeader] = options.csrfToken;
        }
        url += '?f=json&c=airsonic-web';
        var response = await fetch(url, config);
        if (!response.ok || response.redirected) throw new Error('Database request failed');
        if (binary && (response.headers.get('Content-Type') || '').startsWith('application/zip')) return response.blob();
        var result = (await response.json())['subsonic-response'];
        if (!result || result.status !== 'ok') throw new Error(result && result.error ? result.error.message : 'Database request failed');
        if (binary) throw new Error('Missing database archive');
        return result;
    }
    function shown() {
        var type = form.elements.configType.value;
        document.querySelectorAll('.hideawayDatabaseOptions').forEach(function (element) {
            element.hidden = element.id !== type + 'DatabaseOptions' && !(type !== 'BUILTIN' && element.id === 'nonBUILTINDatabaseOptions');
        });
    }
    async function load() {
        var data = (await request('getDatabaseSettings')).databaseSettings;
        form.querySelectorAll('[name]').forEach(function (input) {
            if (input.type === 'checkbox') input.checked = false;
            else input.value = data[input.name] == null ? '' : String(data[input.name]);
        });
        // Keep exact values even if an API client configured a custom interval or retention.
        ['dbBackupInterval', 'dbBackupRetentionCount'].forEach(function (name) {
            var select = form.elements[name], value = String(data[name]);
            if (!Array.from(select.options).some(function (option) { return option.value === value; })) select.add(new Option(value, value));
            select.value = value;
        });
        document.querySelectorAll('.databaseBackuppable').forEach(function (element) { element.hidden = !data.backuppable; });
        shown();
    }
    async function run(work) {
        if (busy) return;
        busy = true; fields.disabled = true; actions.disabled = true; errorBox.textContent = ''; saved.hidden = true;
        try { await work(); } catch (error) { errorBox.textContent = error.message; }
        finally { busy = false; fields.disabled = false; actions.disabled = false; }
    }
    form.elements.configType.addEventListener('change', shown);
    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        body.set('clearPassword', String(form.elements.clearPassword.checked));
        run(async function () { await request('saveDatabaseSettings', body); await load(); saved.hidden = false; });
    });
    document.getElementById('backupdb').addEventListener('click', function () {
        run(async function () { await request('backupDatabase', new URLSearchParams()); });
    });
    document.getElementById('exportdb').addEventListener('click', function () {
        run(async function () {
            var blob = await request('exportDatabase', new URLSearchParams(), true);
            var url = URL.createObjectURL(blob), link = document.createElement('a');
            link.href = url; link.download = 'airsonic-database.zip'; document.body.appendChild(link); link.click(); link.remove();
            window.setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        });
    });
    importForm.addEventListener('submit', function (event) {
        event.preventDefault();
        var body = new FormData(importForm);
        run(async function () { await request('importDatabase', body); importForm.reset(); });
    });
    load().then(function () { fields.disabled = false; actions.disabled = false; }).catch(function (error) { errorBox.textContent = error.message; });
    watchDatabaseStatus({url: options.baseUrl + 'getDatabaseStatus', csrfHeader: options.csrfHeader, csrfToken: options.csrfToken}, function (statuses) {
        operation.textContent = ['backup', 'export', 'import'].filter(function (name) { return statuses[name].version > 0; })
            .map(function (name) { return name + ': ' + statuses[name].message; }).join(' | ');
        ['backup', 'export', 'import'].forEach(function (name) { document.getElementById(name + 'db').disabled = statuses[name].running; });
    });
}
