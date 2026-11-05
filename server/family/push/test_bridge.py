import importlib.util
import http.client
import json
import os
from pathlib import Path
import tempfile
import time
import threading
import unittest
from unittest.mock import patch

import httpx


class BridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        secret = Path(cls.directory.name) / "secret"
        secret.write_text("synthetic-bridge-secret-01234567890123456789")
        os.environ["FAMILY_PUSH_SECRET_FILE"] = str(secret)
        spec = importlib.util.spec_from_file_location("bridge", Path(__file__).with_name("bridge.py"))
        cls.bridge = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.bridge)
        cls.client = httpx.Client

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def setUp(self):
        self.payload = {"v": 1, "callId": "11111111-2222-3333-4444-555555555555",
                        "caller": "alice@example.test", "callee": "bob@example.test",
                        "kind": "voice", "expires": int(time.time() * 1000) + 40000}
        self.bridge.PROJECT = "synthetic-project"
        self.bridge.credentials = type("Credentials", (), {"valid": True, "token": "synthetic-oauth"})()
        self.requests = []

    def transport(self, status=200, body=None):
        def handler(request):
            self.requests.append(request)
            return httpx.Response(status, json=body or {})
        transport = httpx.MockTransport(handler)
        return patch.object(self.bridge.httpx, "Client", lambda **_: self.client(transport=transport))

    def test_android_data_only_high_priority_with_bounded_ttl(self):
        with self.transport():
            self.assertEqual(200, self.bridge.deliver({"platform": "android", "token": "synthetic-fcm"}, self.payload))
        body = json.loads(self.requests[0].content)["message"]
        self.assertNotIn("notification", body)
        self.assertEqual("HIGH", body["android"]["priority"])
        self.assertTrue(1 <= int(body["android"]["ttl"][:-1]) <= 40)
        self.assertEqual(self.payload["callId"], body["data"]["callId"])

    def test_expired_push_never_contacts_provider(self):
        self.payload["expires"] = 0
        with self.transport():
            self.assertEqual(200, self.bridge.deliver({"platform": "android"}, self.payload))
        self.assertEqual([], self.requests)

    def test_fcm_unregistered_removes_only_invalid_token(self):
        body = {"error": {"details": [{"errorCode": "UNREGISTERED"}]}}
        with self.transport(404, body):
            self.assertEqual(410, self.bridge.deliver({"platform": "android", "token": "invalid"}, self.payload))

    def test_provider_outage_is_retryable(self):
        with self.transport(503):
            self.assertEqual(503, self.bridge.deliver({"platform": "android", "token": "synthetic"}, self.payload))

    def test_apns_voip_topic_environment_and_expiration(self):
        key = Path(self.directory.name) / "key"
        key.write_text("synthetic-key-not-used-for-signing")
        environment = {"FAMILY_APNS_KEY_FILE": str(key), "FAMILY_APNS_TEAM_ID": "TESTTEAM",
                       "FAMILY_APNS_KEY_ID": "TESTKEY"}
        self.bridge.apns_token = None
        with patch.dict(os.environ, environment), patch.object(self.bridge.jwt, "encode", return_value="synthetic-jwt"), self.transport():
            self.assertEqual(200, self.bridge.deliver({"platform": "ios", "token": "test-device", "environment": "sandbox"}, self.payload))
        request = self.requests[0]
        self.assertEqual("api.sandbox.push.apple.com", request.url.host)
        self.assertEqual("voip", request.headers["apns-push-type"])
        self.assertTrue(request.headers["apns-topic"].endswith(".voip"))
        self.assertEqual(str(self.payload["expires"] // 1000), request.headers["apns-expiration"])

    def test_bridge_rejects_unauthenticated_requests(self):
        server = self.bridge.ThreadingHTTPServer(("127.0.0.1", 0), self.bridge.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with patch.object(self.bridge, "deliver") as send:
                connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
                connection.request("POST", "/v1/call", body="{}", headers={"Authorization": "Bearer wrong"})
                self.assertEqual(403, connection.getresponse().status)
                connection.close()
                send.assert_not_called()
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=2)

    def test_bridge_accepts_authenticated_bounded_request(self):
        server = self.bridge.ThreadingHTTPServer(("127.0.0.1", 0), self.bridge.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with patch.object(self.bridge, "deliver", return_value=200) as send:
                connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
                connection.request("POST", "/v1/call", body=json.dumps({"registration": {}, "payload": self.payload}),
                                   headers={"Authorization": "Bearer " + self.bridge.SECRET})
                self.assertEqual(200, connection.getresponse().status)
                connection.close()
                send.assert_called_once_with({}, self.payload)
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
