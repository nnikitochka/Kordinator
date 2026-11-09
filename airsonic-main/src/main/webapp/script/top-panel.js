function renderTopPanel(document, settings, avatarUrl) {
    document.getElementById('top-username').textContent = settings.username;
    document.getElementById('top-settings').hidden = !settings.settingsRole;
    ['top-avatar-link', 'top-username'].forEach(function(id) {
        var link = document.getElementById(id);
        if (settings.settingsRole) link.setAttribute('href', 'personalSettings.view');
        else link.removeAttribute('href');
    });
    var avatar = document.getElementById('top-avatar');
    if (settings.showAvatar) avatar.setAttribute('src', avatarUrl + '?username=' + encodeURIComponent(settings.username));
    else avatar.removeAttribute('src');
    avatar.hidden = !settings.showAvatar;
    document.getElementById('top-default-avatar').hidden = settings.showAvatar;
}
