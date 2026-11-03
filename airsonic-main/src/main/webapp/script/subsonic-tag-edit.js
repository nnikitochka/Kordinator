function createTagEdit(options) {
    return {
        edit: async function (values) {
            var form = new URLSearchParams({f: 'json', c: 'airsonic-web'});
            Object.keys(values).forEach(function (key) {
                if (values[key] != null) form.append(key, values[key]);
            });
            var headers = {};
            headers[options.csrfHeader] = options.csrfToken;
            var response = await fetch(options.url, {method: 'POST', credentials: 'same-origin', headers: headers, body: form});
            if (!response.ok || response.redirected) throw new Error('Tag editing request failed');
            var body = (await response.json())['subsonic-response'];
            if (!body || body.status !== 'ok' || !body.tagEdit || !body.tagEdit.result) {
                throw new Error((body && body.error && body.error.message) || 'Tag editing request failed');
            }
            return body.tagEdit.result;
        }
    };
}
