async function loadServerInfo(doc, options) {
    "use strict";
    const fetcher = (options && options.fetch) || doc.defaultView.fetch.bind(doc.defaultView);
    const node = id => doc.getElementById(id);
    const data = node("serverInfoData").dataset;
    const unknown = data.unknown;
    let loading = false;
    async function read(url) {
        const response = await fetcher(url, {credentials: "same-origin", cache: "no-store"});
        if (!response.ok || response.redirected) throw new Error("Server information request failed");
        return response.json();
    }
    const bytes = value => {
        if (!Number.isFinite(value) || value < 0) return unknown;
        const units = ["B", "KB", "MB", "GB", "TB"];
        let unit = 0;
        while (value >= 1024 && unit < units.length - 1) {value /= 1024; unit++;}
        const digits = unit >= 3 ? 2 : unit === 2 ? 1 : 0;
        return value.toLocaleString(undefined, {minimumFractionDigits: digits, maximumFractionDigits: digits}) + " " + units[unit];
    };
    async function refresh() {
        if (loading) return;
        loading = true;
        node("serverInfoError").hidden = true;
        node("serverLogSection").hidden = true;
        node("serverLogError").hidden = true;
        node("serverLogEntries").replaceChildren();
        node("serverLogFile").textContent = "";
        node("serverUpgrade").hidden = true;
        try {
            const info = await read(data.infoUrl);
            if (!info || typeof info.serverInfo !== "string") throw new Error("Invalid server information");
            const brand = typeof info.brand === "string" ? info.brand : unknown;
            node("serverTitle").textContent = node("serverTitle").dataset.template.replace("{0}", brand);
            doc.querySelectorAll("[data-server-brand]").forEach(item => {item.textContent = brand;});
            node("serverVersion").textContent = info.localVersion || unknown;
            const date = info.buildDate ? new Date(info.buildDate) : null;
            node("serverBuildDate").textContent = date && Number.isFinite(date.getTime()) ? date.toLocaleString() : unknown;
            const commit = node("serverBuildNumber");
            commit.textContent = info.buildNumber || unknown;
            commit.removeAttribute("href");
            if (/^[a-f0-9]{7,64}$/i.test(info.buildNumber || "")) commit.href = "https://github.com/airsonic-advanced/airsonic-advanced/commit/" + info.buildNumber;
            node("serverRuntime").textContent = info.serverInfo + " (" + bytes(info.usedMemory) + " / " + bytes(info.totalMemory) + ")";
            if (info.newVersionAvailable && typeof info.latestVersionUrl === "string") {
                let url;
                try {url = new URL(info.latestVersionUrl);} catch (failure) {url = null;}
                if (url && ["https:", "http:"].includes(url.protocol)) {
                    node("serverLatestVersion").textContent = info.latestVersion;
                    node("serverUpgrade").querySelectorAll("a").forEach(link => {
                        link.removeAttribute("onclick"); link.href = url.href; link.target = "_blank"; link.rel = "noopener noreferrer";
                    });
                    node("serverUpgrade").hidden = false;
                }
            }
            if (info.canReadLog === true) {
                node("serverLogSection").hidden = false;
                try {
                    const log = await read(data.logUrl);
                    if (!log || !Array.isArray(log.logEntries)) throw new Error("Invalid server log");
                    node("serverLogFile").textContent = log.logFile;
                    log.logEntries.forEach(line => {
                        const row = doc.createElement("tr"), cell = doc.createElement("td");
                        cell.textContent = line; row.appendChild(cell); node("serverLogEntries").appendChild(row);
                    });
                    node("serverLogError").hidden = log.available === true;
                } catch (failure) {node("serverLogError").hidden = false;}
            }
        } catch (failure) {node("serverInfoError").hidden = false;}
        finally {loading = false;}
    }
    node("serverRefresh").addEventListener("click", event => {event.preventDefault(); return refresh();});
    await refresh();
}
if (typeof module !== "undefined") module.exports = {loadServerInfo};
