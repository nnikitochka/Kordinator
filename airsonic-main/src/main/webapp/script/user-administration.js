function initUserAdministration(options) {
    var form = document.getElementById('command');
    var selector = document.getElementById('userSelector');
    var errorBox = document.getElementById('userSettingsError');
    var fields = document.getElementById('userFields');
    var selection = new URLSearchParams(location.search);
    var params = new URLSearchParams({f: 'json', c: 'airsonic-web'});
    if (selection.has('username')) params.set('username', selection.get('username'));
    else if (selection.has('userIndex')) params.set('userIndex', selection.get('userIndex'));
    function show(id, visible) { document.getElementById(id).hidden = !visible; }
    function checkbox(name) { return form.querySelector('input[type=checkbox][name=' + name + ']'); }
    function passwordFields() {
        var ldap = checkbox('ldapAuthenticated').checked;
        show('passwordChangeCheckboxTable', !ldap && form.elements.newUser.value !== 'true');
        show('passwordChangeTable', form.elements.newUser.value === 'true' || (!ldap && checkbox('passwordChange').checked));
    }
    function display(data) {
        var user = data.user;
        ((data.users || {}).user || []).forEach(function (name) { selector.add(new Option(name, name)); });
        selector.value = user.newUser ? '' : user.username;
        form.elements.newUser.value = String(user.newUser);
        form.elements.username.value = user.username;
        form.elements.username.readOnly = !user.newUser;
        form.elements.email.value = user.email;
        ['adminRole', 'settingsRole', 'streamRole', 'jukeboxRole', 'downloadRole', 'uploadRole',
            'shareRole', 'coverArtRole', 'commentRole', 'ldapAuthenticated'].forEach(function (name) {
            checkbox(name).checked = user[name];
        });
        show('adminRow', !user.currentUser);
        show('deleteSection', !user.newUser && !user.currentUser && user.username !== 'admin');
        show('ldapSection', data.ldapEnabled && user.username !== 'admin');
        show('transcodeWarning', !data.transcodingSupported);
        var folderBody = document.getElementById('musicFolders');
        ((data.folders || {}).folder || []).forEach(function (folder) {
            var row = document.createElement('tr'), cell = row.insertCell(), input = document.createElement('input');
            input.type = 'checkbox'; input.name = 'allowedMusicFolderIds'; input.value = folder.id;
            input.checked = folder.allowed; input.className = 'checkbox'; cell.appendChild(input);
            row.insertCell().textContent = folder.name;
            row.insertCell().textContent = folder.path;
            folderBody.appendChild(row);
        });
        show('folderSection', folderBody.children.length > 0);
        var schemes = form.elements.transcodeSchemeName;
        ((data.schemes || {}).scheme || []).forEach(function (scheme) { schemes.add(new Option(scheme.description, scheme.name)); });
        schemes.value = user.transcodeSchemeName;
        passwordFields();
        fields.disabled = false;
    }
    selector.addEventListener('change', function () {
        location.href = 'userSettings.view' + (selector.value ? '?username=' + encodeURIComponent(selector.value) : '');
    });
    checkbox('passwordChange').addEventListener('change', passwordFields);
    checkbox('ldapAuthenticated').addEventListener('change', passwordFields);
    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        var body = new URLSearchParams(new FormData(form));
        ['adminRole', 'settingsRole', 'streamRole', 'jukeboxRole', 'downloadRole', 'uploadRole',
            'shareRole', 'coverArtRole', 'commentRole', 'ldapAuthenticated', 'passwordChange', 'deleteUser'].forEach(function (name) {
            body.set(name, String(checkbox(name).checked));
        });
        body.set('f', 'json'); body.set('c', 'airsonic-web');
        var headers = {}; headers[options.csrfHeader] = options.csrfToken;
        try {
            var response = await fetch(options.saveUrl, {method: 'POST', credentials: 'same-origin', headers: headers, body: body});
            if (!response.ok || response.redirected) throw new Error('Request failed');
            var result = (await response.json())['subsonic-response'];
            if (!result || result.status !== 'ok') throw new Error(result && result.error ? result.error.message : 'Request failed');
            if (parent.frames.left) parent.frames.left.location.reload();
            location.href = 'userSettings.view';
        } catch (error) { errorBox.textContent = error.message; }
    });
    fetch(options.readUrl + '?' + params, {credentials: 'same-origin'}).then(function (response) {
        if (!response.ok || response.redirected) throw new Error('Request failed');
        return response.json();
    }).then(function (body) {
        var result = body['subsonic-response'];
        if (!result || result.status !== 'ok' || !result.websiteUserAdministration)
            throw new Error(result && result.error ? result.error.message : 'Request failed');
        display(result.websiteUserAdministration);
    }).catch(function (error) { errorBox.textContent = error.message; });
}
