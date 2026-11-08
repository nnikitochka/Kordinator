function initCredentialsSettings(options) {
    var form = document.getElementById('credentials');
    var createForm = document.getElementById('newCreds');
    var adminForm = document.getElementById('credentialAdmin');
    var errorBox = document.getElementById('credentialsError');
    var saved = document.getElementById('credentialsSaved');
    var snapshot;
    function busy(value) {
        ['credentialsFields', 'newCredentialsFields', 'credentialAdminFields'].forEach(function (id) {
            var fieldset = document.getElementById(id); if (fieldset) fieldset.disabled = value;
        });
    }
    async function request(url, body) {
        var config = {credentials: 'same-origin', cache: 'no-store'};
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
    function localTime(value) {
        if (!value) return '';
        var date = new Date(value);
        return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, -1);
    }
    function instant(value) { return value ? new Date(value).toISOString() : ''; }
    function populate(select, entries, selected) {
        select.replaceChildren();
        entries.forEach(function (entry) { select.add(new Option(entry.name, entry.id)); });
        if (selected != null) select.value = selected;
    }
    function cell(row, value) {
        var td = row.insertCell(); td.style.padding = '0 0.5em';
        if (typeof value === 'string') td.textContent = value; else td.append(value);
    }
    function bindApplication() {
        var app = (snapshot.app || []).find(function (item) { return item.id === createForm.elements.app.value; });
        if (!app) return;
        createForm.elements.appUsername.disabled = !app.usernameRequired;
        createForm.elements.appUsername.value = '';
        populate(createForm.elements.encoder, snapshot.encoder.filter(function (encoder) {
            return app.nonDecodableEncodersAllowed || encoder.decodable;
        }), app.nonDecodableEncodersAllowed ? snapshot.preferredEncoder : snapshot.preferredDecodableEncoder);
    }
    function render(data) {
        snapshot = data;
        document.getElementById('ldapCredentialWarning').hidden = !data.ldapAuthEnabled;
        document.getElementById('airsonicCredentials').replaceChildren();
        document.getElementById('thirdPartyCredentials').replaceChildren();
        var count = (data.credential || []).filter(function (item) { return item.app === 'AIRSONIC'; }).length;
        (data.credential || []).forEach(function (credential, index) {
            var row = document.getElementById(credential.app === 'AIRSONIC' ? 'airsonicCredentials' : 'thirdPartyCredentials').insertRow();
            row.dataset.id = credential.id;
            cell(row, String(index));
            var app = data.app.find(function (item) { return item.id === credential.app; });
            cell(row, app.name); cell(row, credential.username); cell(row, credential.comment);
            cell(row, credential.created ? new Date(credential.created).toLocaleString() : '');
            cell(row, credential.updated ? new Date(credential.updated).toLocaleString() : '');
            var encoder = document.createElement('select'); encoder.name = 'encoder[' + credential.id + ']';
            var allowed = credential.decodable ? data.encoder.filter(function (item) {
                return app.nonDecodableEncodersAllowed || item.decodable;
            }) : [];
            if (!allowed.some(function (item) { return item.id === credential.encoder; })) {
                allowed = [{id: credential.encoder, name: credential.encoder}].concat(allowed);
            }
            populate(encoder, allowed, credential.encoder); cell(row, encoder);
            var expires = document.createElement('input'); expires.type = 'datetime-local'; expires.step = '0.001';
            expires.name = 'expires[' + credential.id + ']'; expires.value = localTime(credential.expires); cell(row, expires);
            var deletion = document.createElement('input'); deletion.type = 'checkbox'; deletion.className = 'delete-cred';
            deletion.name = 'delete[' + credential.id + ']'; deletion.disabled = credential.app === 'AIRSONIC' && count === 1;
            cell(row, deletion);
        });
        populate(createForm.elements.app, data.app, createForm.elements.app.value || 'AIRSONIC'); bindApplication();
        if (adminForm && data.adminControls) {
            var admin = data.adminControls;
            ['legacy', 'open', 'defaultAdmin'].forEach(function (key) {
                document.getElementById(key + 'Credentials').hidden = !admin[key + 'CredsPresent'];
            });
            populate(adminForm.elements.nonDecodableEncoder, data.encoder.filter(function (item) { return !item.decodable; }), admin.nonDecodableEncoder);
            populate(adminForm.elements.decodableEncoder, data.encoder.filter(function (item) { return item.decodable; }), admin.decodableEncoder);
            adminForm.elements.preferNonDecodable.checked = admin.preferNonDecodable;
            adminForm.elements.jwtKey.value = ''; adminForm.elements.encryptionKey.value = '';
            adminForm.elements.jwtKey.type = 'password'; adminForm.elements.encryptionKey.type = 'password';
            document.getElementById('encryptionKeySalt').textContent = '';
        }
    }
    async function mutate(url, body, after) {
        errorBox.textContent = ''; saved.hidden = true; busy(true);
        try {
            render((await request(url, body)).credentialManagement);
            if (after) after(); saved.hidden = false;
        } catch (error) { errorBox.textContent = error.message; }
        finally { busy(false); }
    }
    form.addEventListener('submit', function (event) {
        event.preventDefault();
        try {
            var body = new URLSearchParams();
            (snapshot.credential || []).forEach(function (credential) {
                var key = credential.id;
                body.append('id', key); body.set('encoder[' + key + ']', form.elements['encoder[' + key + ']'].value);
                body.set('expires[' + key + ']', instant(form.elements['expires[' + key + ']'].value));
                body.set('delete[' + key + ']', String(form.elements['delete[' + key + ']'].checked));
            });
            if (body.has('id')) mutate(options.updateUrl, body);
        } catch (error) { errorBox.textContent = error.message; }
    });
    form.addEventListener('reset', function (event) { event.preventDefault(); if (snapshot) render(snapshot); });
    createForm.addEventListener('submit', function (event) {
        event.preventDefault();
        try {
            var body = new URLSearchParams(new FormData(createForm)); body.set('expires', instant(createForm.elements.expires.value));
            mutate(options.createUrl, body, function () { createForm.reset(); bindApplication(); document.getElementById('createNewCreds').hidden = true; });
        } catch (error) { errorBox.textContent = error.message; }
    });
    createForm.elements.app.addEventListener('change', bindApplication);
    document.getElementById('createcredsbutton').addEventListener('click', function () { document.getElementById('createNewCreds').hidden = false; });
    document.getElementById('cancelNewCredential').addEventListener('click', function () { createForm.reset(); bindApplication(); document.getElementById('createNewCreds').hidden = true; });
    if (adminForm) {
        adminForm.addEventListener('submit', function (event) {
            event.preventDefault();
            var action = event.submitter && event.submitter.value;
            var body = new URLSearchParams({action: action});
            if (action === 'encoders') {
                body.set('nonDecodableEncoder', adminForm.elements.nonDecodableEncoder.value);
                body.set('decodableEncoder', adminForm.elements.decodableEncoder.value);
                body.set('preferNonDecodable', String(adminForm.elements.preferNonDecodable.checked));
            } else if (action === 'jwtKey' || action === 'encryptionKey') body.set(action, adminForm.elements[action].value);
            mutate(options.adminUrl, body);
        });
        document.getElementById('showCredentialKeys').addEventListener('click', async function () {
            errorBox.textContent = ''; busy(true);
            try {
                var keys = (await request(options.keysUrl)).credentialKeys;
                adminForm.elements.jwtKey.value = keys.jwtKey; adminForm.elements.encryptionKey.value = keys.encryptionKey;
                adminForm.elements.jwtKey.type = 'text'; adminForm.elements.encryptionKey.type = 'text';
                document.getElementById('encryptionKeySalt').textContent = keys.encryptionKeySalt;
            } catch (error) { errorBox.textContent = error.message; }
            finally { busy(false); }
        });
    }
    request(options.readUrl).then(function (result) { render(result.credentialManagement); busy(false); })
        .catch(function (error) { errorBox.textContent = error.message; });
}
