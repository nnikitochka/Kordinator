async function loadGettingStarted(doc) {
    const config = doc.getElementById('gettingStartedData').dataset;
    const error = doc.getElementById('gettingStartedError');
    const hide = doc.getElementById('gettingStartedHide');
    try {
        const response = await fetch(config.url, {credentials: 'same-origin', cache: 'no-store'});
        if (!response.ok) throw new Error('Unable to load getting started');
        const data = await response.json();
        if (typeof data.runningAsRoot !== 'boolean' || typeof data.canDisable !== 'boolean') throw new Error('Invalid response');
        doc.getElementById('gettingStartedRoot').hidden = !data.runningAsRoot;
        hide.hidden = !data.canDisable;
        hide.onclick = async function (event) {
            event.preventDefault();
            hide.hidden = true;
            error.hidden = true;
            try {
                const headers = {};
                headers[config.csrfHeader] = config.csrfToken;
                const result = await fetch(config.url, {method: 'POST', credentials: 'same-origin', headers: headers});
                if (!result.ok) throw new Error('Unable to disable getting started');
                alert(config.message);
                window.location.href = config.home;
            } catch (failure) {
                error.hidden = false;
                hide.hidden = false;
            }
        };
    } catch (failure) {
        error.hidden = false;
    }
}
