function initShareSettings(options) {
    var form = document.getElementById('shareSettings');
    var fields = document.getElementById('shareFields');
    var rows = document.getElementById('shareRows');
    var errorBox = document.getElementById('shareSettingsError');
    var saved = document.getElementById('shareSettingsSaved');
    var shares = [];

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
    function date(value) { return value ? new Date(value).toLocaleDateString() : ''; }
    function cell(row, content) {
        var td = row.insertCell(); td.style.paddingLeft = '1em';
        if (typeof content === 'string') td.textContent = content;
        else if (content) td.append(content);
        return td;
    }
    function input(type, name, value) {
        var element = document.createElement('input');
        element.type = type; element.name = name;
        if (value != null) element.value = value;
        return element;
    }
    function render() {
        rows.replaceChildren();
        shares.forEach(function (share) {
            var entry = (share.entry || [])[0];
            // Match the old page: rows without media accessible to this user are omitted.
            if (!entry) return;
            var row = rows.insertRow(); row.dataset.id = share.id;
            var link = document.createElement('a');
            var url = new URL(share.url, location.href);
            if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error('Invalid share URL');
            link.href = url.href; link.target = '_blank'; link.rel = 'noopener noreferrer';
            link.textContent = decodeURIComponent(url.pathname.split('/').pop());
            cell(row, link); cell(row, share.username);
            var description = input('text', 'description[' + share.id + ']', share.description || ''); description.size = 40;
            cell(row, description); cell(row, date(share.lastVisited));
            cell(row, String(share.visitCount)).style.textAlign = 'right';
            var media = document.createElement('a');
            media.href = options.mediaUrl + '?id=' + encodeURIComponent(entry.isDir ? entry.id : entry.parent || entry.id);
            var name = entry.isDir ? entry.title : entry.album || entry.title;
            media.title = name; media.textContent = name.length > 30 ? name.slice(0, 27) + '...' : name;
            cell(row, media); cell(row, date(share.expires));
            var expiration = document.getElementById('shareExpirationOptions').content.cloneNode(true);
            expiration.querySelectorAll('input').forEach(function (radio) { radio.name = 'expireIn[' + share.id + ']'; });
            cell(row, expiration);
            cell(row, input('checkbox', 'delete[' + share.id + ']')).style.textAlign = 'center';
        });
    }
    async function read() { return (await request(options.readUrl)).shares.share || []; }
    async function load() { shares = await read(); render(); }

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        errorBox.textContent = ''; saved.hidden = true;
        var body = new FormData(form);
        var now = Date.now();
        var plan = [];
        var completed = 0;
        fields.disabled = true;
        try {
            // Build and validate the entire form before sending writes.
            shares.forEach(function (share) {
                var key = 'description[' + share.id + ']';
                if (!body.has(key)) return;
                var deletion = body.has('delete[' + share.id + ']');
                var days = body.get('expireIn[' + share.id + ']');
                if (days != null && ['0', '7', '30', '365'].indexOf(days) === -1) throw new Error('Invalid expiration');
                var description = body.get(key).trim();
                if (deletion) plan.push({url: options.deleteUrl, body: new URLSearchParams({id: share.id})});
                else if (description !== (share.description || '') || days != null) {
                    var params = new URLSearchParams({id: share.id});
                    // An omitted description clears it under the standard updateShare contract.
                    if (description) params.set('description', description);
                    // Subsonic uses epoch milliseconds. Omission preserves the current expiration; zero clears it.
                    if (days != null) params.set('expires', days === '0' ? '0' : String(now + Number(days) * 86400000));
                    plan.push({url: options.updateUrl, body: params});
                }
            });
            for (var operation of plan) { await request(operation.url, operation.body); completed++; }
            if (body.has('deleteExpired')) {
                // Refresh after edits so a renewed share is not deleted using its old expiration.
                for (var share of await read()) {
                    if (share.expires && new Date(share.expires).getTime() < Date.now()) {
                        await request(options.deleteUrl, new URLSearchParams({id: share.id})); completed++;
                    }
                }
            }
            await load(); form.elements.deleteExpired.checked = false; saved.hidden = false;
        } catch (error) {
            errorBox.textContent = error.message;
            // Successful REST writes remain committed if a later write fails; reload to make retries safe.
            if (completed) {
                try { await load(); } catch (reloadError) { errorBox.textContent += ' ' + reloadError.message; }
            }
        } finally { fields.disabled = false; }
    });
    load().then(function () { fields.disabled = false; }).catch(function (error) { errorBox.textContent = error.message; });
}
