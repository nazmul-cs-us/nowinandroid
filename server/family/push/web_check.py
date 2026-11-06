#!/usr/bin/env python3
"""Read-only VM checks for the temporary helper; never prints configuration secrets."""
import argparse
import http.client
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import time
from urllib.parse import urlsplit


def probe(settings, *, connect_host="127.0.0.1", context=None):
    """Reach the local listener while verifying its public certificate hostname."""
    parsed = urlsplit(settings["url"])
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.query or parsed.fragment or parsed.path != "/family-helper/"):
        raise ValueError("Invalid helper URL")
    context = context or ssl.create_default_context()

    def get(name, content_type):
        client = http.client.HTTPConnection(parsed.hostname, parsed.port or 443, timeout=3)
        try:
            client.sock = context.wrap_socket(
                socket.create_connection((connect_host, parsed.port or 443), timeout=3),
                server_hostname=parsed.hostname)
            client.request("GET", parsed.path + name)
            response = client.getresponse()
            body = response.read(4 * 1024 * 1024 + 1)
            if (response.status != 200 or len(body) > 4 * 1024 * 1024 or not body
                    or response.getheader("Content-Type", "").split(";")[0] != content_type):
                raise ValueError("Helper resource unavailable")
            return body
        finally:
            client.close()

    key = json.loads(get("api/key", "application/json"))
    if key.get("publicKey") != settings["publicKey"]:
        raise ValueError("Helper key differs from installed configuration")
    get("", "text/html")
    get("style.css", "text/css")
    get("app.js", "text/javascript")
    get("sw.js", "text/javascript")
    get("open.html", "text/html")
    get("open.js", "text/javascript")
    manifest = json.loads(get("manifest.webmanifest", "application/manifest+json"))
    if manifest.get("display") != "standalone" or manifest.get("scope") != parsed.path:
        raise ValueError("Home Screen manifest is invalid")
    if not get("icon.png", "image/png").startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Home Screen icon is invalid")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="/etc/mongooseim/family-web.json")
    args = parser.parse_args()
    for service in ("mongooseim", "deenly-family-push", "deenly-family-web"):
        result = subprocess.run(["systemctl", "is-active", "--quiet", service],
                                capture_output=True, timeout=5)
        if result.returncode:
            raise RuntimeError("Service inactive: " + service)
        print("PASS: " + service + " active.")
    module = Path("/opt/mongooseim-6.10.0/lib/mongooseim-6.10.0/ebin/mod_deenly_family_web.beam")
    if not module.is_file():
        raise RuntimeError("Install the Family server modules before enabling the helper.")
    settings = json.loads(Path(args.config).read_text())
    if not os.access(settings["privateKey"], os.R_OK):
        raise RuntimeError("The service user cannot read its VAPID key.")
    # A just-restarted systemd process can be active before its listener binds.
    for attempt in range(3):
        try:
            probe(settings)
            break
        except (ConnectionError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(0.5)
    print("PASS: verified TLS, configured public key, helper pages and Home Screen assets.")
    print("NEXT: allow HTTPS port 8443 for the phone network, then pair the installed helper.")
    print("Local readiness does not verify the GCP firewall or real iPhone push delivery.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.TimeoutExpired, http.client.HTTPException) as error:
        # The exact exception may include paths or data; print only known local guidance.
        detail = str(error) if isinstance(error, RuntimeError) else type(error).__name__
        raise SystemExit("FAIL: temporary helper readiness: " + detail)
