#!/usr/bin/env python3
"""Temporary, explicitly paired Web Push alerts; no native account passwords."""
import base64
from contextlib import contextmanager
from concurrent.futures import ThreadPoolExecutor
import hashlib
import hmac
import json
import logging
import os
from pathlib import Path
import secrets
import sqlite3
import ssl
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
import uuid
import requests

PREFIX = "/family-helper/"
CONFIG = "/etc/mongooseim/family-web.json"
PROVIDERS = {"web.push.apple.com", "fcm.googleapis.com", "updates.push.services.mozilla.com"}


def unbase64(value):
    return base64.b64decode(value + "=" * (-len(value) % 4), altchars=b"-_", validate=True)


def valid_endpoint(endpoint):
    parsed = urlsplit(endpoint)
    if (len(endpoint) > 4096 or parsed.scheme != "https" or parsed.hostname not in PROVIDERS
            or parsed.port not in (None, 443) or parsed.username or parsed.password or parsed.fragment):
        raise ValueError("Invalid push endpoint")


class ProviderSession(requests.Session):
    def request(self, method, url, *args, **kwargs):
        valid_endpoint(url)
        kwargs["allow_redirects"] = False
        return super().request(method, url, *args, **kwargs)


def valid_subscription(value):
    from cryptography.hazmat.primitives.asymmetric import ec
    endpoint = value["endpoint"]
    valid_endpoint(endpoint)
    key, auth = unbase64(value["keys"]["p256dh"]), unbase64(value["keys"]["auth"])
    if len(key) != 65 or len(auth) != 16:
        raise ValueError("Invalid push key")
    ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), key)
    return {"endpoint": endpoint, "keys": {"p256dh": value["keys"]["p256dh"], "auth": value["keys"]["auth"]}}


class Store:
    def __init__(self, path, secret, domain="xmpp.starception.com"):
        self.path, self.secret, self.domain = str(path), secret.encode(), domain
        if len(self.secret) < 32:
            raise ValueError("Invalid bridge secret")
        with self.connect() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS used(nonce TEXT PRIMARY KEY, expires INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS subscriptions(
                    id TEXT PRIMARY KEY, account TEXT NOT NULL, owner TEXT NOT NULL,
                    generation INTEGER NOT NULL, endpoint_hash TEXT UNIQUE NOT NULL,
                    subscription TEXT NOT NULL, revoke_hash TEXT NOT NULL, expires INTEGER NOT NULL);
                CREATE INDEX IF NOT EXISTS by_account ON subscriptions(account);
                CREATE INDEX IF NOT EXISTS by_expiry ON subscriptions(expires);
            """)
        os.chmod(self.path, 0o600)

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=5)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    def cleanup(self):
        with self.connect() as db:
            now = int(time.time())
            db.execute("DELETE FROM used WHERE expires <= ?", (now,))
            db.execute("DELETE FROM subscriptions WHERE expires <= ?", (now,))

    def pair(self, code, subscription):
        if not isinstance(code, str) or len(code) > 4096:
            raise ValueError("Invalid pairing code")
        encoded, signature = code.split(".")
        expected = hmac.new(self.secret, ("family-web-pair-v1:" + encoded).encode(), hashlib.sha256).digest()
        if not hmac.compare_digest(unbase64(signature), expected):
            raise ValueError("Invalid pairing code")
        claim = json.loads(unbase64(encoded))
        now = int(time.time())
        account, owner, nonce, generation = claim["account"], claim["owner"], claim["nonce"], claim["generation"]
        if (claim["purpose"] != "family-web-pair-v1" or type(claim["expires"]) is not int
                or not now < claim["expires"] <= now + 300 or type(generation) is not int or generation < 0
                or not isinstance(account, str) or len(account) > 254
                or account.count("@") != 1 or not account.endswith("@" + self.domain)
                or any(c.isspace() or c in "/<>" for c in account)
                or not isinstance(owner, str) or not 16 <= len(owner) <= 80
                or not isinstance(nonce, str) or not 24 <= len(nonce) <= 80):
            raise ValueError("Invalid pairing code")
        subscription = valid_subscription(subscription)
        fingerprint = hashlib.sha256(subscription["endpoint"].encode()).hexdigest()
        identifier, revoke = uuid.uuid4().hex, secrets.token_urlsafe(32)
        expires = now + 86400
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM used WHERE expires < ?", (now,))
            db.execute("DELETE FROM subscriptions WHERE expires <= ?", (now,))
            count = db.execute("SELECT count(*) FROM subscriptions WHERE account=? AND endpoint_hash!=?", (account, fingerprint)).fetchone()[0]
            if count >= 8:
                raise ValueError("Too many helper installations")
            db.execute("INSERT INTO used VALUES (?,?)", (nonce, claim["expires"]))
            db.execute("DELETE FROM subscriptions WHERE endpoint_hash=?", (fingerprint,))
            db.execute("INSERT INTO subscriptions VALUES (?,?,?,?,?,?,?,?)", (identifier, account, owner,
                generation, fingerprint, json.dumps(subscription), hashlib.sha256(revoke.encode()).hexdigest(), expires))
        return {"id": identifier, "revoke": revoke, "expires": expires}

    def revoke(self, identifier, token):
        if not isinstance(identifier, str) or not isinstance(token, str) or len(token) > 128:
            raise ValueError("Invalid revocation")
        with self.connect() as db:
            row = db.execute("SELECT revoke_hash FROM subscriptions WHERE id=?", (identifier,)).fetchone()
            if row and not hmac.compare_digest(row[0], hashlib.sha256(token.encode()).hexdigest()):
                raise ValueError("Invalid revocation")
            db.execute("DELETE FROM subscriptions WHERE id=?", (identifier,))

    def targets(self, account, generations):
        result = []
        with self.connect() as db:
            rows = db.execute("SELECT * FROM subscriptions WHERE account=?", (account,)).fetchall()
            for row in rows:
                if row["expires"] <= int(time.time()) or generations.get(row["owner"]) != row["generation"]:
                    db.execute("DELETE FROM subscriptions WHERE id=?", (row["id"],))
                else:
                    result.append((row["id"], json.loads(row["subscription"])))
        return result

    def delete(self, identifier):
        with self.connect() as db:
            db.execute("DELETE FROM subscriptions WHERE id=?", (identifier,))


_settings = None
_store = None
_init_lock = threading.Lock()


def configured():
    global _settings, _store
    with _init_lock:
        if _settings is None:
            settings = json.loads(Path(os.environ.get("FAMILY_WEB_CONFIG_FILE", CONFIG)).read_text())
            secret = Path(os.environ.get("FAMILY_PUSH_SECRET_FILE", "/etc/mongooseim/push-bridge-secret")).read_text().strip()
            store = Store(os.environ.get("FAMILY_WEB_DATABASE", "/var/lib/deenly-family-web/helper.sqlite3"), secret, settings["domain"])
            _settings, _store = settings, store
    return _settings, _store


def deliver(registration, payload, *, settings=None, store=None, sender=None):
    from pywebpush import webpush, WebPushException
    if settings is None or store is None:
        settings, store = configured()
    sender = sender or webpush
    if payload["callee"] != registration["token"] or int(payload["expires"]) <= int(time.time() * 1000):
        return 200
    identifier = str(uuid.UUID(payload["callId"]))
    data = json.dumps({"v": 1, "callId": identifier, "kind": payload["kind"], "expires": payload["expires"]})
    ttl = max(1, min(45, (int(payload["expires"]) - int(time.time() * 1000)) // 1000))
    targets = store.targets(registration["token"], registration.get("generations", {}))
    def send(target):
        identifier, subscription = target
        try:
            with ProviderSession() as session:
                sender(subscription_info=subscription, data=data, vapid_private_key=settings["privateKey"],
                    vapid_claims={"sub": settings["subject"]}, ttl=ttl,
                    headers={"Urgency": "high", "Topic": str(uuid.UUID(payload["callId"])).replace("-", "")}, timeout=3,
                    requests_session=session)
            return True
        except WebPushException as error:
            code = error.response.status_code if error.response is not None else 0
            if code in (404, 410):
                store.delete(identifier)
                return True
            logging.warning("Web Push delivery rejected (HTTP %s)", code)
            return False
        except Exception as error:
            logging.warning("Web Push failed (%s)", type(error).__name__)
            return False
    if not targets:
        return 200
    # Send bounded device fanout concurrently so a slow phone does not delay another.
    with ThreadPoolExecutor(max_workers=min(8, len(targets))) as pool:
        delivered = list(pool.map(send, targets))
    return 200 if all(delivered) else 503


class Handler(BaseHTTPRequestHandler):
    server_version = "DeenlyHelper"
    def log_message(self, *_):
        pass

    def reply(self, status, value, content_type="application/json"):
        data = value if isinstance(value, bytes) else json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == PREFIX + "api/key":
            return self.reply(200, {"publicKey": self.server.settings["publicKey"]})
        names = {PREFIX: ("index.html", "text/html; charset=utf-8"),
                 **{PREFIX + name: (name, mime) for name, mime in [
                     ("index.html", "text/html; charset=utf-8"), ("open.html", "text/html; charset=utf-8"),
                     ("app.js", "text/javascript"), ("open.js", "text/javascript"), ("sw.js", "text/javascript"),
                     ("style.css", "text/css"), ("manifest.webmanifest", "application/manifest+json"), ("icon.png", "image/png")]}}
        if path not in names:
            return self.reply(404, {"error": "Not found"})
        name, mime = names[path]
        return self.reply(200, (self.server.static / name).read_bytes(), mime)

    def do_POST(self):
        try:
            origin = self.headers.get("Origin", "")
            expected = self.server.settings["url"].split(PREFIX)[0]
            if origin != expected:
                return self.reply(403, {"error": "Open the installed call helper first."})
            if not self.server.allow(self.client_address[0]):
                return self.reply(429, {"error": "Please wait before retrying."})
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 16384:
                return self.reply(413, {"error": "Invalid request size"})
            body = json.loads(self.rfile.read(size))
            if self.path == PREFIX + "api/subscribe":
                value = self.server.store.pair(body["code"], body["subscription"])
                return self.reply(200, value)
            if self.path == PREFIX + "api/unsubscribe":
                self.server.store.revoke(body["id"], body["revoke"])
                return self.reply(200, {"ok": True})
            return self.reply(404, {"error": "Not found"})
        except (ValueError, KeyError, TypeError, sqlite3.IntegrityError):
            return self.reply(400, {"error": "Pairing failed. Create a fresh code in Deenly and try again."})
        except Exception as error:
            logging.warning("Helper request failed (%s)", type(error).__name__)
            return self.reply(503, {"error": "Call alerts are temporarily unavailable."})


class Server(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, settings, store):
        self.settings, self.store = settings, store
        self.static = Path(__file__).with_name("web")
        self.limit = threading.BoundedSemaphore(32)
        self.tls_lock = threading.Lock()
        self.cert_version = None
        self.rates = {}
        self.next_cleanup = 0
        self.rate_lock = threading.Lock()
        super().__init__(address, Handler)

    def service_actions(self):
        if time.monotonic() >= self.next_cleanup:
            self.store.cleanup()
            self.next_cleanup = time.monotonic() + 60

    def allow(self, address):
        now = int(time.time()) // 60
        with self.rate_lock:
            self.rates = {key: entry for key, entry in self.rates.items() if entry[0] == now}
            minute, count = self.rates.get(address, (now, 0))
            if count >= 12 or len(self.rates) >= 4096:
                return False
            self.rates[address] = (minute, count + 1)
            return True

    def process_request(self, request, address):
        if not self.limit.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, address)
        except BaseException:
            self.limit.release()
            raise

    def process_request_thread(self, request, address):
        try:
            cert = self.settings.get("certificate", "/etc/mongooseim/certs/fullchain.pem")
            key = self.settings.get("certificateKey", "/etc/mongooseim/certs/privkey.pem")
            stamp = (os.stat(cert).st_mtime_ns, os.stat(key).st_mtime_ns)
            with self.tls_lock:
                if stamp != self.cert_version:
                    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                    context.minimum_version = ssl.TLSVersion.TLSv1_2
                    context.load_cert_chain(cert, key)
                    self.context, self.cert_version = context, stamp
                context = self.context
            request.settimeout(10)
            request = context.wrap_socket(request, server_side=True)
            super().process_request_thread(request, address)
        except (OSError, ssl.SSLError):
            request.close()
        finally:
            self.limit.release()


if __name__ == "__main__":
    settings, store = configured()
    Server(("0.0.0.0", urlsplit(settings["url"]).port or 443), settings, store).serve_forever()
