#!/usr/bin/env python3
"""Run a disposable, real-WAR Spring baseline smoke check.

This intentionally starts only the executable WAR supplied by the caller. It
uses a fresh Airsonic home and XDG data directory, and removes both on exit.
The process exit status is recorded, but by itself it does not prove that every
application scheduler or resource was shut down gracefully.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from html.parser import HTMLParser
from pathlib import Path
from typing import Any


STARTUP_TIMEOUT_SECONDS = 120
SIGTERM_GRACE_SECONDS = 30
HTTP_TIMEOUT_SECONDS = 12
TEST_USERNAME = "admin"
TEST_PASSWORD = "admin"
REST_CLIENT = "packaged-smoke"
TOMCAT_PORT = re.compile(rb"Tomcat started on port\s+(\d+)", re.IGNORECASE)
SHUTDOWN_LIMITATION = (
    "Process exit after SIGTERM is process-level evidence only; this smoke check cannot confirm that every scheduler and application resource shut down gracefully."
)


class SmokeFailure(RuntimeError):
    """A smoke assertion failed; messages must not contain URLs or secrets."""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class LoginFormParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.login_frame = False
        self.fields: dict[str, str] = {}
        self.login_button = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag == "div" and values.get("id") == "loginframe":
            self.login_frame = True
        if tag == "input":
            name = values.get("name")
            if name in ("j_username", "j_password"):
                self.fields[name] = values.get("placeholder") or ""
            if name == "submit":
                self.login_button = bool(values.get("value"))


def repo_root() -> Path:
    return Path(__file__).resolve().parents[3]


def source_metadata() -> dict[str, str | None]:
    root = repo_root()
    app_version: str | None = None
    try:
        import xml.etree.ElementTree as ET

        pom = ET.parse(root / "pom.xml").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        app_version = pom.findtext("m:version", namespaces=ns)
    except (OSError, ET.ParseError):
        pass
    api_version: str | None = None
    xsd = root / "subsonic-rest-api/src/main/resources/subsonic-rest-api.xsd"
    try:
        xsd_root = ET.parse(xsd).getroot()
        api_version = xsd_root.attrib.get("version")
    except (OSError, ET.ParseError):
        pass
    commit: str | None = None
    try:
        completed = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=5,
        )
        commit = completed.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return {
        "application_version": app_version,
        "rest_api_version": api_version,
        # This identifies the checked-out source tree, not necessarily the WAR.
        "repository_head": commit,
    }


def war_metadata(war: Path) -> dict[str, str | None]:
    digest = hashlib.sha256()
    with war.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    build_revision: str | None = None
    try:
        with zipfile.ZipFile(war) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
        unfolded = re.sub(r"\r?\n[ \t]", "", manifest)
        revision_names = {
            "build-revision", "git-commit-id", "implementation-build", "scm-revision",
        }
        for line in unfolded.splitlines():
            key, separator, value = line.partition(":")
            if separator and key.strip().lower() in revision_names and value.strip():
                build_revision = value.strip()
                break
    except (OSError, KeyError, zipfile.BadZipFile):
        pass
    return {"war_sha256": digest.hexdigest(), "war_build_revision": build_revision}


def fetch(opener: urllib.request.OpenerDirector, url: str, *, headers: dict[str, str] | None = None,
          timeout: int = HTTP_TIMEOUT_SECONDS) -> tuple[int, bytes, dict[str, str]]:
    request = urllib.request.Request(url, headers=headers or {}, method="GET")
    try:
        with opener.open(request, timeout=timeout) as response:
            return response.status, response.read(), dict(response.headers.items())
    except urllib.error.HTTPError as response:
        return response.code, response.read(), dict(response.headers.items())
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        # Avoid including request URLs: REST URLs contain the disposable test password.
        raise SmokeFailure(f"HTTP request failed ({type(exc).__name__})") from None


def authenticated_opener() -> urllib.request.OpenerDirector:
    return urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())


def ping_url(base: str, api_version: str, output_format: str) -> str:
    query = urllib.parse.urlencode({
        "u": TEST_USERNAME,
        "p": TEST_PASSWORD,
        "v": api_version,
        "c": REST_CLIENT,
        "f": output_format,
    })
    return f"{base}/rest/ping?{query}"


def assert_ping(body: bytes, output_format: str, api_version: str) -> None:
    try:
        if output_format == "xml":
            root = ET.fromstring(body)
            status = root.attrib.get("status")
            version = root.attrib.get("version")
        else:
            payload = json.loads(body.decode("utf-8"))
            response = payload.get("subsonic-response", {})
            status = response.get("status")
            version = response.get("version")
    except (ET.ParseError, UnicodeDecodeError, json.JSONDecodeError, AttributeError) as exc:
        raise SmokeFailure(f"REST ping {output_format} response could not be parsed ({type(exc).__name__})") from None
    if status != "ok":
        raise SmokeFailure(f"REST ping {output_format} returned an unsuccessful response")
    if version != api_version:
        raise SmokeFailure(f"REST ping {output_format} returned an unexpected protocol version")


def wait_for_start(process: subprocess.Popen[bytes], log_path: Path) -> tuple[int, float]:
    started = time.monotonic()
    deadline = started + STARTUP_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise SmokeFailure("WAR process exited before Tomcat startup")
        try:
            match = TOMCAT_PORT.search(log_path.read_bytes())
        except OSError:
            match = None
        if match:
            port = int(match.group(1))
            if not 1 <= port <= 65535:
                raise SmokeFailure("Tomcat reported an invalid loopback port")
            return port, time.monotonic() - started
        time.sleep(0.2)
    raise SmokeFailure("WAR startup exceeded 120 seconds")


def stop_process(process: subprocess.Popen[bytes] | None) -> dict[str, Any]:
    if process is None:
        return {
            "sigterm_sent": False,
            "exited_after_sigterm_within_30s": None,
            "exit_code": None,
            "accepted_exit_code": None,
        }
    sent = process.poll() is None
    if sent:
        try:
            process.send_signal(signal.SIGTERM)
        except ProcessLookupError:
            sent = False
    exited_after_sigterm = False
    try:
        process.wait(timeout=SIGTERM_GRACE_SECONDS)
        exited_after_sigterm = sent
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
    accepted_exit_code = process.returncode in (0, -signal.SIGTERM, 128 + signal.SIGTERM)
    return {
        "sigterm_sent": sent,
        "exited_after_sigterm_within_30s": exited_after_sigterm,
        "exit_code": process.returncode,
        "accepted_exit_code": accepted_exit_code,
    }


def port_is_released(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(1)
        return probe.connect_ex(("127.0.0.1", port)) != 0


def child_environment() -> dict[str, str]:
    # Keep only OS/runtime basics. In particular, do not inherit application
    # configuration or JVM option injection from an interactive/CI shell.
    allowed = {"PATH", "LANG", "LC_ALL", "LC_CTYPE", "TZ", "JAVA_HOME"}
    env = {name: value for name, value in os.environ.items() if name in allowed}
    for name in tuple(env):
        normalized = name.upper()
        if normalized.startswith(("SPRING_", "AIRSONIC_")):
            env.pop(name, None)
    return env


def launch(war: Path, home: Path, xdg_home: Path, log_path: Path) -> subprocess.Popen[bytes]:
    env = child_environment()
    env["XDG_DATA_HOME"] = str(xdg_home)
    db_url = (
        f"jdbc:hsqldb:file:{home / 'db' / 'airsonic'};hsqldb.tx=mvcc;"
        "sql.enforce_size=false;sql.char_literal=false;sql.nulls_first=false;"
        "sql.pad_space=false;hsqldb.defrag_limit=50;shutdown=true"
    )
    command = [
        "java",
        f"-Dairsonic.home={home}",
        f"-Duser.home={home}",
        "-jar",
        str(war),
        "--server.address=127.0.0.1",
        "--server.port=0",
        "--server.servlet.context-path=/airsonic",
        "--logging.level.org.springframework.boot.web.embedded.tomcat=INFO",
        f"--spring.datasource.url={db_url}",
        "--spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
        "--spring.datasource.username=sa",
        "--spring.datasource.password=",
    ]
    with log_path.open("wb") as log_file:
        return subprocess.Popen(
            command,
            cwd=str(home),
            env=env,
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            close_fds=True,
        )


def run_cycle(war: Path, home: Path, xdg_home: Path, cycle: int,
              metadata: dict[str, str | None], results: dict[str, Any], log_path: Path) -> None:
    process: subprocess.Popen[bytes] | None = None
    port: int | None = None
    try:
        process = launch(war, home, xdg_home, log_path)
        port, startup_seconds = wait_for_start(process, log_path)
        base = f"http://127.0.0.1:{port}/airsonic"
        opener = authenticated_opener()
        cycle_result: dict[str, Any] = {
            "startup_seconds": round(startup_seconds, 3),
            "port": port,
            "checks": {},
        }
        results["cycles"].append(cycle_result)

        api_version = metadata.get("rest_api_version")
        if not api_version:
            raise SmokeFailure("Could not determine REST protocol version from source XSD")

        if cycle == 1:
            for output_format, expected_type in (("xml", "application/xml"), ("json", "application/json")):
                started = time.monotonic()
                code, body, headers = fetch(opener, ping_url(base, api_version, output_format))
                if code != 200:
                    raise SmokeFailure(f"Authenticated REST ping {output_format} returned HTTP {code}")
                assert_ping(body, output_format, api_version)
                content_type = headers.get("Content-Type", "").lower()
                if expected_type not in content_type and not (output_format == "xml" and "text/xml" in content_type):
                    raise SmokeFailure(f"REST ping {output_format} returned an unexpected content type")
                cycle_result["checks"][f"ping_{output_format}"] = {
                    "status_code": code,
                    "seconds": round(time.monotonic() - started, 3),
                    "protocol_version": api_version,
                }

            static_path = repo_root() / "airsonic-main/src/main/webapp/script/utils.js"
            static_bytes = static_path.read_bytes()
            started = time.monotonic()
            static_code, received_static, _ = fetch(opener, f"{base}/script/utils.js")
            if static_code != 200 or received_static != static_bytes:
                raise SmokeFailure("Static /script/utils.js did not match the repository bytes")
            cycle_result["checks"]["static_utils_js"] = {
                "status_code": static_code,
                "seconds": round(time.monotonic() - started, 3),
                "sha256": hashlib.sha256(received_static).hexdigest(),
                "bytes": len(received_static),
            }

            anon_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
            started = time.monotonic()
            index_code, _, index_headers = fetch(anon_opener, f"{base}/index")
            location = index_headers.get("Location", "")
            parsed_location = urllib.parse.urlsplit(location)
            if index_code != 302 or not parsed_location.path.startswith("/airsonic/login"):
                raise SmokeFailure("Anonymous /index did not redirect to the /airsonic login page")
            cycle_result["checks"]["anonymous_index"] = {
                "status_code": index_code,
                "seconds": round(time.monotonic() - started, 3),
                "redirect": "login",
            }

            started = time.monotonic()
            login_code, login_body, _ = fetch(anon_opener, f"{base}/login", headers={"Accept-Language": "en"})
            parser = LoginFormParser()
            parser.feed(login_body.decode("utf-8", errors="replace"))
            if login_code != 200 or not parser.login_frame or not all(parser.fields.values()) or not parser.login_button:
                raise SmokeFailure("Login page is missing localized username/password/submit markers")
            cycle_result["checks"]["login_page"] = {
                "status_code": login_code,
                "seconds": round(time.monotonic() - started, 3),
                "localized_fields": sorted(parser.fields),
                "submit_marker": parser.login_button,
            }

            unknown_url = f"{base}/smoke-no-such-resource"
            token = base64.b64encode(f"{TEST_USERNAME}:{TEST_PASSWORD}".encode("utf-8")).decode("ascii")
            started = time.monotonic()
            unknown_code, _, _ = fetch(opener, unknown_url, headers={"Authorization": f"Basic {token}"})
            if unknown_code != 404:
                raise SmokeFailure(f"Authenticated unknown context resource returned HTTP {unknown_code}, expected 404")
            cycle_result["checks"]["authenticated_unknown"] = {
                "status_code": unknown_code,
                "seconds": round(time.monotonic() - started, 3),
            }
        else:
            started = time.monotonic()
            code, body, _ = fetch(opener, ping_url(base, api_version, "xml"))
            if code != 200:
                raise SmokeFailure(f"Warm-restart REST ping returned HTTP {code}")
            assert_ping(body, "xml", api_version)
            cycle_result["checks"]["warm_restart_ping_xml"] = {
                "status_code": code,
                "seconds": round(time.monotonic() - started, 3),
                "protocol_version": api_version,
            }
    finally:
        stop_result = stop_process(process)
        if port is not None:
            stop_result["port_released"] = port_is_released(port)
        if cycle <= len(results["cycles"]):
            results["cycles"][cycle - 1]["shutdown"] = stop_result
        if process is not None and not stop_result["exited_after_sigterm_within_30s"]:
            raise SmokeFailure("WAR process did not exit after SIGTERM within 30 seconds")
        if process is not None and not stop_result["accepted_exit_code"]:
            raise SmokeFailure("WAR process returned an unexpected exit code after SIGTERM")
        if port is not None and not stop_result["port_released"]:
            raise SmokeFailure("Loopback port remained reachable after process termination")


def run(war: Path, log_dir: Path | None = None) -> dict[str, Any]:
    metadata = source_metadata()
    result: dict[str, Any] = {
        "status": "failed",
        "war": war.name,
        **metadata,
        **war_metadata(war),
        "startup_timeout_seconds": STARTUP_TIMEOUT_SECONDS,
        "sigterm_grace_seconds": SIGTERM_GRACE_SECONDS,
        "limitations": [SHUTDOWN_LIMITATION],
        "cycles": [],
    }
    with tempfile.TemporaryDirectory(prefix="airsonic-packaged-smoke-") as temp_name:
        temp_root = Path(temp_name)
        home = temp_root / "airsonic.home"
        xdg_home = temp_root / "xdg-data"
        home.mkdir()
        xdg_home.mkdir()
        try:
            for cycle in (1, 2):
                log_path = temp_root / f"cycle-{cycle}.log"
                run_cycle(war, home, xdg_home, cycle, metadata, result, log_path)
                # Logs live under the automatically removed TemporaryDirectory.
            result["status"] = "passed"
        except Exception as exc:
            result["error"] = str(exc) if isinstance(exc, SmokeFailure) else f"Operation failed ({type(exc).__name__})"
            if log_dir is not None:
                log_dir.mkdir(parents=True, exist_ok=True)
                for cycle in (1, 2):
                    source = temp_root / f"cycle-{cycle}.log"
                    if source.is_file():
                        (log_dir / source.name).write_bytes(source.read_bytes())
                result["saved_logs"] = [
                    name for name in ("cycle-1.log", "cycle-2.log")
                    if (log_dir / name).is_file()
                ]
    return result


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("war_positional", nargs="?", type=Path, help="executable WAR to smoke test")
    parser.add_argument("--war", dest="war_option", type=Path, help="executable WAR to smoke test")
    parser.add_argument("--output", type=Path, help="write the JSON report to this file (default: stdout)")
    parser.add_argument(
        "--log-dir",
        type=Path,
        help="copy process logs here on failure (use a temporary directory such as /tmp)",
    )
    args = parser.parse_args(argv)
    if args.war_positional and args.war_option:
        parser.error("pass the WAR either positionally or with --war, not both")
    args.war = args.war_option or args.war_positional
    if not args.war:
        parser.error("an executable WAR path is required")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    war = args.war.expanduser().resolve()
    try:
        if not war.is_file():
            raise SmokeFailure("WAR path is not a file")
        report = run(war, args.log_dir.expanduser() if args.log_dir else None)
    except SmokeFailure as exc:
        report = {
            "status": "failed",
            "war": war.name,
            **source_metadata(),
            "error": str(exc),
            "limitations": [SHUTDOWN_LIMITATION],
        }
    except (OSError, subprocess.SubprocessError) as exc:
        report = {
            "status": "failed",
            "war": war.name,
            **source_metadata(),
            **(war_metadata(war) if war.is_file() else {}),
            "error": f"Operation failed ({type(exc).__name__})",
            "limitations": [SHUTDOWN_LIMITATION],
        }
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.write(rendered)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
