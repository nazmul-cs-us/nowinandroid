#!/usr/bin/env python3
"""Live Family protocol acceptance test. Run on the VM as mongooseim.

Creates temporary accounts, exercises authenticated IQ commands, never prints
credentials, and removes its accounts. Does not capture microphone/camera media.
"""
import argparse
import base64
from pathlib import Path
import tomllib
import json
import secrets
import socket
import ssl
import subprocess
import uuid
import xml.etree.ElementTree as ET

DOMAIN = "xmpp.starception.com"
NS = "urn:starception:family:1"
CLIENT = "{jabber:client}"
STREAM = "{http://etherx.jabber.org/streams}"
SASL = "{urn:ietf:params:xml:ns:xmpp-sasl}"
BIND = "{urn:ietf:params:xml:ns:xmpp-bind}"


def account(action, **fields):
    args = ["/usr/local/bin/mongooseimctl", "account", action]
    for key, value in fields.items():
        args += ["--" + key, value]
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Account command failed; raw output withheld.")
    body = json.loads(result.stdout)
    if body.get("errors"):
        raise RuntimeError("Account operation failed.")
    return body["data"]["account"][action]


def connection_target(args=None, config_path="/etc/mongooseim/mongooseim.toml"):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", help="C2s listener IP/host; defaults to the local MongooseIM configuration")
    parser.add_argument("--port", type=int, help="C2s port; defaults to the configured port, or 5222 with --host")
    options = parser.parse_args(args)
    host, port = options.host, options.port
    if host is None:
        try:
            config = tomllib.loads(Path(config_path).read_text())
            listeners = config["listen"]["c2s"]
            if len(listeners) != 1:
                raise ValueError("ambiguous listener")
            listener = listeners[0]
            host = listener["ip_address"]
            port = port if port is not None else listener.get("port", 5222)
        except (OSError, ValueError, KeyError, TypeError):
            parser.error("Cannot determine the local C2s listener. Pass --host and optionally --port.")
    port = 5222 if port is None else port
    if not isinstance(host, str) or not host.strip() or not isinstance(port, int) or not 1 <= port <= 65535:
        parser.error("Specify a valid C2s host and TCP port.")
    # Wildcard bind addresses describe listeners, not connection destinations.
    host = {"0.0.0.0": "127.0.0.1", "::": "::1"}.get(host.strip(), host.strip())
    return host, port


class Client:
    def __init__(self, username, password, endpoint):
        self.sock = socket.create_connection(endpoint, timeout=15)
        self.jid = username + "@" + DOMAIN
        try:
            self.open_stream()
            self.send("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")
            self.wait(lambda e: e.tag == "{urn:ietf:params:xml:ns:xmpp-tls}proceed")
            self.sock = ssl.create_default_context().wrap_socket(self.sock, server_hostname=DOMAIN)
            self.open_stream()
            credential = base64.b64encode(("\0" + username + "\0" + password).encode()).decode()
            self.send(f"<auth xmlns='{SASL[1:-1]}' mechanism='PLAIN'>{credential}</auth>")
            self.wait(lambda e: e.tag == SASL + "success")
            self.open_stream()
            self.send("<iq type='set' id='bind'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>"
                      f"<resource>smoke-{uuid.uuid4()}</resource></bind></iq>")
            result = self.wait(lambda e: e.tag == CLIENT + "iq" and e.get("id") == "bind")
            require(result.get("type") == "result", "Resource binding failed.")
            self.send("<iq type='set' id='session'><session xmlns='urn:ietf:params:xml:ns:xmpp-session'/></iq>")
            result = self.wait(lambda e: e.tag == CLIENT + "iq" and e.get("id") == "session")
            require(result.get("type") == "result", "Session establishment failed.")
            self.send("<presence/>")
        except BaseException:
            self.sock.close()
            raise

    def send(self, text):
        self.sock.sendall(text.encode())

    def open_stream(self):
        self.parser = ET.XMLPullParser(events=["end"])
        self.send(f"<stream:stream to='{DOMAIN}' version='1.0' xmlns='jabber:client' "
                  "xmlns:stream='http://etherx.jabber.org/streams'>")
        self.wait(lambda e: e.tag == STREAM + "features")

    def wait(self, predicate):
        while True:
            for _, element in self.parser.read_events():
                if element.tag in (STREAM + "error", SASL + "failure"):
                    raise RuntimeError("XMPP stream/authentication failed.")
                if predicate(element):
                    return element
            data = self.sock.recv(65536)
            if not data:
                raise RuntimeError("XMPP connection closed.")
            self.parser.feed(data)

    def request(self, action, expected_error=None, **fields):
        request_id = str(uuid.uuid4())
        iq = ET.Element("iq", {"type": "set", "id": request_id, "to": DOMAIN})
        child = ET.SubElement(iq, "family", {"xmlns": NS})
        child.text = json.dumps(dict(v=1, action=action, **fields))
        self.send(ET.tostring(iq, encoding="unicode"))
        reply = self.wait(lambda e: e.tag == CLIENT + "iq" and e.get("id") == request_id)
        require(reply.get("type") == "result", "Family module did not return a protocol result.")
        text = reply.findtext("{" + NS + "}family")
        require(text is not None, "Family module response missing.")
        result = json.loads(text)
        if expected_error:
            require(result.get("ok") is False and result.get("error") == expected_error,
                    "Expected authorization denial was not returned.")
        else:
            require(result.get("ok") is True, "Family operation failed; inspect the VM configuration locally.")
        return result

    def event(self, name, call_id=None, stage=None):
        def matches(element):
            if element.tag != CLIENT + "message" or element.get("from") != DOMAIN:
                return False
            try:
                obj = json.loads(element.findtext(CLIENT + "body") or "{}")
            except json.JSONDecodeError:
                return False
            return (obj.get("v") == 1 and obj.get("event") == name
                    and (call_id is None or obj.get("callId", obj.get("call", {}).get("id")) == call_id)
                    and (stage is None or obj.get("call", {}).get("stage") == stage))
        result = self.wait(matches)
        return json.loads(result.findtext(CLIENT + "body"))


def require(value, message):
    if not value:
        raise RuntimeError(message)


def main():
    endpoint = connection_target()
    print(f"Testing C2s listener {endpoint[0]}:{endpoint[1]} with verified TLS identity {DOMAIN}.", flush=True)
    users, clients = [], []
    try:
        credentials = []
        for _ in range(3):
            name = "family-smoke-" + secrets.token_hex(6)
            password = secrets.token_urlsafe(32)
            account("registerUser", username=name, domain=DOMAIN, password=password)
            users.append(name + "@" + DOMAIN)
            credentials.append((name, password))
            clients.append(Client(name, password, endpoint))
        a, b, outsider = clients
        print("PASS: three real XMPP logins over verified TLS.", flush=True)
        call_id = str(uuid.uuid4())
        a.request("invite_call", "not_family", peer=b.jid, callId=call_id, kind="voice")
        a.request("invite", peer=b.jid)
        a.request("accept_member", "invitation_not_found", peer=b.jid)
        outsider.request("accept_member", "invitation_not_found", peer=a.jid)
        incoming = b.request("snapshot")["members"]
        require(any(x["jid"] == a.jid and x["status"] == "incoming" for x in incoming), "Invitation missing.")
        b.request("accept_member", peer=a.jid)
        print("PASS: only the invited member can accept; non-family calls denied.", flush=True)
        a.request("presence", active=True, activityVersion=1)
        update = b.event("presence")
        require(update.get("jid") == a.jid and update.get("presence") == "online", "Live family presence was not delivered.")
        require(update.get("presenceVersion", 0) > 0 and update.get("presenceEpoch"), "Presence revision missing.")
        a.request("presence", active=False, activityVersion=2)
        require(b.event("presence").get("presence") == "offline", "Background presence was not delivered.")
        a.request("presence", active=True, activityVersion=1)
        member = next(x for x in b.request("snapshot")["members"] if x["jid"] == a.jid)
        require(member.get("presence") == "offline", "An old foreground update restored online status.")
        require(outsider.request("snapshot")["members"] == [], "Presence exposed non-family data.")
        print("PASS: live accepted-family presence, background updates and stale-update protection.", flush=True)
        a.request("invite_call", peer=b.jid, callId=call_id, kind="voice")
        b.event("call", call_id, "ringing")
        outsider.request("accept_call", "call_not_found", callId=call_id)
        b.request("accept_call", callId=call_id)
        a.event("call", call_id, "active")
        for client in (a, b):
            relay = client.request("turn", callId=call_id)["iceServers"]
            require(relay and relay[0].get("credential") and relay[0].get("username"), "Relay credentials missing.")
        print("PASS: accepted family call and participant-scoped TURN issuance.", flush=True)
        other_device = Client(*credentials[1], endpoint=endpoint); clients.append(other_device)
        other_device.request("accept_call", "answered_elsewhere", callId=call_id)
        other_device.request("turn", "not_active", callId=call_id)
        print("PASS: a second device cannot take over an answered call.", flush=True)
        payload = {"type": "offer", "sdp": "synthetic-diagnostic-offer"}
        a.request("signal", callId=call_id, payload=payload)
        require(b.event("signal", call_id)["payload"] == payload, "Authorized signalling was not delivered.")
        outsider.request("signal", "call_not_found", callId=call_id, payload=payload)
        print("PASS: signalling delivered only for an authorized call participant.", flush=True)
        b.request("remove_member", peer=a.jid)
        require(a.request("snapshot")["calls"] == [], "Removed family call remained active.")
        a.request("turn", "not_active", callId=call_id)
        a.request("invite_call", "not_family", peer=b.jid, callId=str(uuid.uuid4()), kind="voice")
        print("PASS: family removal ends the call and revokes further calling/relay issuance.", flush=True)
    finally:
        for client in clients:
            client.sock.close()
        failures = []
        for user in users:
            try:
                account("removeUser", user=user)
            except Exception:
                failures.append(user)
        if failures:
            raise RuntimeError("Remove these diagnostic accounts manually: " + ", ".join(failures))
        print("Temporary accounts removed.", flush=True)


if __name__ == "__main__":
    main()
