/* Password recovery is public; mutations use the anonymous session's CSRF token. */
async function loadPasswordRecovery(doc, options) {
    "use strict";
    options = options || {};
    const view = doc.defaultView;
    const fetcher = options.fetch || view.fetch.bind(view);
    const form = doc.getElementById("recoveryForm");
    const submit = doc.getElementById("recoverySubmit");
    const errors = Array.from(doc.querySelectorAll("[data-recovery-error]"));
    const error = key => errors.forEach(node => { node.hidden = node.dataset.recoveryError !== key; });
    let config;
    let busy = false;
    async function read(response) {
        if (!response.ok || response.redirected) throw new Error("Recovery request failed");
        return response.json();
    }
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (!config || busy) return;
        busy = true;
        submit.disabled = true;
        error(null);
        try {
            const body = new URLSearchParams({usernameOrEmail: doc.getElementById("usernameOrEmail").value});
            if (config.recaptchaSiteKey) body.set("g-recaptcha-response", view.grecaptcha ? view.grecaptcha.getResponse() : "");
            const result = await read(await fetcher(form.action, {method: "POST", credentials: "same-origin", cache: "no-store",
                headers: {[config.csrfHeader]: config.csrfToken, "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"}, body: body.toString()}));
            if (typeof result.sentTo === "string") {
                doc.getElementById("recoveryInputs").hidden = true;
                const success = doc.getElementById("recoverySuccess");
                success.textContent = success.dataset.template.replace("{0}", result.sentTo);
                success.hidden = false;
            } else {
                error(errors.some(node => node.dataset.recoveryError === result.error) ? result.error : "recover.error.sendfailed");
                if (config.recaptchaSiteKey && view.grecaptcha) view.grecaptcha.reset();
            }
        } catch (failure) {
            error("recover.error.sendfailed");
            if (config.recaptchaSiteKey && view.grecaptcha) view.grecaptcha.reset();
        } finally {
            busy = false;
            submit.disabled = false;
        }
    });
    try {
        config = await read(await fetcher(form.action, {credentials: "same-origin", cache: "no-store"}));
        if (typeof config.csrfHeader !== "string" || !config.csrfHeader || typeof config.csrfToken !== "string" || !config.csrfToken) {
            config = null;
            throw new Error("Missing CSRF token");
        }
        if (config.recaptchaSiteKey) {
            doc.getElementById("recoveryCaptcha").dataset.sitekey = config.recaptchaSiteKey;
            const script = doc.createElement("script");
            script.src = "https://www.google.com/recaptcha/api.js";
            script.async = true;
            script.defer = true;
            doc.head.appendChild(script);
        }
        submit.disabled = false;
    } catch (failure) {
        config = null;
        error("recover.error.sendfailed");
    }
}
if (typeof module !== "undefined") module.exports = {loadPasswordRecovery};
