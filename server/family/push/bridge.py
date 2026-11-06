#!/usr/bin/env python3
"""Loopback-only push provider bridge. No user-facing HTTP API or credential logs."""
import hmac
import json
import logging
import os
from pathlib import Path
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading

import google.auth
from google.auth.transport.requests import Request
import httpx
import jwt

SECRET = Path(os.environ.get("FAMILY_PUSH_SECRET_FILE", "/etc/mongooseim/push-bridge-secret")).read_text().strip()
if len(SECRET) < 32:
    raise RuntimeError("Push bridge secret must have at least 32 characters")
PROJECT = os.environ.get("FAMILY_FCM_PROJECT", "")
TOPIC = os.environ.get("FAMILY_APNS_BUNDLE_ID", "com.starception.submission") + ".voip"
LOCK = threading.Lock()
credentials = None
apns_token = None
apns_issued = 0


def deliver(registration, payload):
    global credentials, apns_token, apns_issued
    now = int(time.time())
    expires = int(payload["expires"]) // 1000
    if expires <= now:
        return 200
    token = registration["token"]
    if registration["platform"] == "web":
        import web_helper
        return web_helper.deliver(registration, payload)
    with httpx.Client(http2=True, timeout=6) as client:
        if registration["platform"] == "android":
            if not PROJECT:
                return 503
            with LOCK:
                if credentials is None:
                    credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/firebase.messaging"])
                if not credentials.valid:
                    request = Request()
                    def bounded_request(*args, **kwargs):
                        kwargs.setdefault("timeout", 5)
                        return request(*args, **kwargs)
                    credentials.refresh(bounded_request)
                access = credentials.token
            response = client.post(f"https://fcm.googleapis.com/v1/projects/{PROJECT}/messages:send",
                headers={"Authorization": "Bearer " + access}, json={"message": {
                    "token": token, "data": {k: str(v) for k, v in payload.items()},
                    "android": {"priority": "HIGH", "ttl": f"{max(1, expires-now)}s"}}})
            if response.is_success:
                return 200
            errors = response.json().get("error", {}).get("details", [])
            logging.warning("FCM delivery rejected (HTTP %s)", response.status_code)
            return 410 if any(e.get("errorCode") == "UNREGISTERED" for e in errors) else 503
        if registration["platform"] != "ios" or not os.environ.get("FAMILY_APNS_KEY_FILE"):
            return 503
        with LOCK:
            if not apns_token or now - apns_issued > 3000:
                key = Path(os.environ["FAMILY_APNS_KEY_FILE"]).read_text()
                apns_token = jwt.encode({"iss": os.environ["FAMILY_APNS_TEAM_ID"], "iat": now}, key,
                    algorithm="ES256", headers={"kid": os.environ["FAMILY_APNS_KEY_ID"]})
                apns_issued = now
            access = apns_token
        host = "api.sandbox.push.apple.com" if registration.get("environment") == "sandbox" else "api.push.apple.com"
        response = client.post(f"https://{host}/3/device/{token}",
            headers={"authorization": "bearer " + access, "apns-topic": TOPIC,
                     "apns-push-type": "voip", "apns-priority": "10", "apns-expiration": str(expires)},
            json={"aps": {"content-available": 1}, **payload})
        if not response.is_success:
            logging.warning("APNs delivery rejected (HTTP %s)", response.status_code)
        return 200 if response.is_success else 410 if response.status_code == 410 else 503


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Tokens and provider response bodies must not reach access logs.

    def do_POST(self):
        status = 400
        try:
            if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + SECRET):
                status = 403
            elif self.path != "/v1/call":
                status = 404
            elif not 0 < int(self.headers.get("Content-Length", "0")) <= 16384:
                status = 413
            else:
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                status = deliver(body["registration"], body["payload"])
        except Exception as error:
            logging.warning("Push bridge failed (%s)", type(error).__name__)
            status = 503
        self.send_response(status)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8789), Handler).serve_forever()
