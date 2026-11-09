function createStatusStatistics(options) {
    async function request(name, init) {
        var response = await fetch(options.baseUrl + name + '?f=json&c=airsonic-web', Object.assign({
            credentials: 'same-origin', cache: 'no-store'
        }, init));
        if (!response.ok || response.redirected) throw new Error('Statistics request failed');
        var body = (await response.json())['subsonic-response'];
        if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Statistics request failed');
        return body;
    }
    async function read(name, container, field) {
        var body = await request(name);
        if (!body[container]) throw new Error('Missing statistics');
        var rows = body[container][field];
        if (rows == null) rows = [];
        if (!Array.isArray(rows)) throw new Error('Invalid statistics');
        return rows;
    }
    return {
        health: async function () {
            var body = await request('getServerHealth');
            if (!body.serverHealth || typeof body.serverHealth.status !== 'string' ||
                (body.serverHealth.component != null && !Array.isArray(body.serverHealth.component))) {
                throw new Error('Invalid server health');
            }
            return body.serverHealth;
        },
        caches: function () { return read('getCacheStatistics', 'cacheStatistics', 'cache'); },
        clearCache: function (name) {
            var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
            if (options.csrfHeader && options.csrfToken) headers[options.csrfHeader] = options.csrfToken;
            return request('clearCache', {method: 'POST', headers: headers, body: 'cache=' + encodeURIComponent(name)});
        },
        transfers: function () { return read('getTransferStatistics', 'transferStatistics', 'transfer'); },
        users: function () { return read('getUserTransferStatistics', 'userTransferStatistics', 'user'); },
        pathWatchers: function () { return read('getPathWatchers', 'pathWatchers', 'watcher'); },
        scheduledTasks: function () { return read('getScheduledTasks', 'scheduledTasks', 'task'); }
    };
}

function renderTransferStatistics(document, rows, labels) {
    var body = document.querySelector('#transfersTable > tbody');
    body.replaceChildren();
    rows.forEach(function (row, index) {
        var tr = document.createElement('tr');
        [labels[row.transferType], row.playerDescription, row.username, row.path, row.bytesTransferred].forEach(function (value, column) {
            var td = document.createElement('td');
            td.textContent = value == null ? labels.null : value;
            if (column === 1) {
                td.appendChild(document.createElement('br'));
                td.appendChild(document.createTextNode(row.playerType == null ? labels.null : row.playerType));
            }
            tr.appendChild(td);
        });
        var cell = document.createElement('td'), chart = document.createElement('canvas');
        chart.id = 'transfersChart' + index;
        cell.appendChild(chart); tr.appendChild(cell); body.appendChild(tr);
    });
}


function renderScheduledTasks(document, rows, labels) {
    function date(value) {
        if (value == null) return labels.never;
        if (value.startsWith('-1000000000-01-01T')) return labels.incalculable;
        var parsed = new Date(value);
        return isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
    }
    var body = document.querySelector('#scheduledTasksTable > tbody');
    body.replaceChildren();
    rows.forEach(function (row) {
        var tr = document.createElement('tr');
        [row.name, labels[row.type] || labels.null, date(row.created), date(row.firstRun),
            date(row.lastRun), date(row.nextRun), row.scheduledBy].forEach(function (value) {
            var td = document.createElement('td');
            td.textContent = value == null ? labels.null : value;
            tr.appendChild(td);
        });
        body.appendChild(tr);
    });
}

function renderPathWatchers(document, rows) {
    var body = document.querySelector('#pathWatcherTable > tbody');
    body.replaceChildren();
    rows.forEach(function (row) {
        var tr = document.createElement('tr');
        [row.name, row.path].forEach(function (value) {
            var td = document.createElement('td');
            td.textContent = value;
            tr.appendChild(td);
        });
        body.appendChild(tr);
    });
}

function renderCacheStatistics(document, rows, labels, clearCache) {
    var body = document.querySelector('#cachesTable > tbody');
    body.replaceChildren();
    rows.forEach(function (row) {
        var tr = document.createElement('tr');
        var total = row.hits + row.misses;
        var usage = row.hits != null && row.misses != null && total > 0 ? row.hits / total * 100 : 'N/A';
        [row.name, usage, row.hits, row.misses, row.puts, row.removals].forEach(function (value, index) {
            var td = document.createElement('td');
            td.className = ['cachename', 'cacheusage', 'cachehits', 'cachemiss', 'cacheputs', 'cacheremovals'][index];
            td.textContent = value == null ? 'N/A' : value;
            tr.appendChild(td);
        });
        var cell = document.createElement('td'), button = document.createElement('button');
        button.type = 'button'; button.textContent = labels.cacheevict;
        button.addEventListener('click', async function () {
            button.disabled = true;
            try { await clearCache(row.name); }
            catch (error) { console.error(error); }
            finally { button.disabled = false; }
        });
        cell.appendChild(button); tr.appendChild(cell); body.appendChild(tr);
    });
}

function renderServerHealth(document, health) {
    var body = document.querySelector('#healthTable > tbody');
    body.replaceChildren();
    function row(name, status, details) {
        var tr = document.createElement('tr');
        [name, status, details == null ? '' : details].forEach(function (value) {
            var td = document.createElement('td');
            td.textContent = value;
            tr.appendChild(td);
        });
        body.appendChild(tr);
    }
    row('server', health.status, null);
    function components(items, prefix) {
        (items || []).forEach(function (item) {
            var name = prefix + item.name;
            row(name, item.status, item.details);
            components(item.component, name + '/');
        });
    }
    components(health.component, '');
}
