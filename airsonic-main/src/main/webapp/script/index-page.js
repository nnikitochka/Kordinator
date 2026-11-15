function resolveIndexMainTarget(value, appRootPath, currentUrl) {
    var base = new URL(appRootPath, currentUrl);
    var fallback = new URL('nowPlaying.view', base);
    if (typeof value !== 'string' || !value || /[\\\u0000-\u001f]/.test(value) || /%(2f|5c)/i.test(value)) {
        return fallback.href;
    }
    try {
        var target = new URL(value, base);
        if (target.origin !== base.origin || !target.pathname.startsWith(base.pathname) ||
                (target.protocol !== 'http:' && target.protocol !== 'https:')) {
            return fallback.href;
        }
        return target.href;
    } catch (e) {
        return fallback.href;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {resolveIndexMainTarget: resolveIndexMainTarget};
}
