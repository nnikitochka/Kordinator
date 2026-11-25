Several issues may affect Chromecast usage.

This is an informational reference for the Kordinator web client.

- The Presentation API used by Chromecast is unavailable over plain HTTP in current Chromium-based browsers. If Kordinator is opened over HTTP, the Cast button may not appear.
- Chromecast may reject self-signed HTTPS certificates when fetching media. A trusted certificate is recommended when casting from an HTTPS Kordinator instance.
- Chromecast can play HTTP media, but the browser Presentation API still requires a secure context.
