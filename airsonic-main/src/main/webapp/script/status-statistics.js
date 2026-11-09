function createStatusStatistics(options) {
    async function read(name, container, field) {
        var response = await fetch(options.baseUrl + name + '?f=json&c=airsonic-web', {
            credentials: 'same-origin', cache: 'no-store'
        });
        if (!response.ok || response.redirected) throw new Error('Statistics request failed');
        var body = (await response.json())['subsonic-response'];
        if (!body || body.status !== 'ok') throw new Error((body && body.error && body.error.message) || 'Statistics request failed');
        if (!body[container]) throw new Error('Missing statistics');
        var rows = body[container][field];
        if (rows == null) rows = [];
        if (!Array.isArray(rows)) throw new Error('Invalid statistics');
        return rows;
    }
    return {
        transfers: function () { return read('getTransferStatistics', 'transferStatistics', 'transfer'); },
        users: function () { return read('getUserTransferStatistics', 'userTransferStatistics', 'user'); },
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
