"""Exercise signup smoke's real XML reader against rejected/closed socket streams."""
import socket
import threading
import unittest
import xml.etree.ElementTree as ET
from signup_smoke import RegistrationClient, check_plaintext_rejection
from smoke import XmppStreamFailure


class PlaintextRejectionTests(unittest.TestCase):
    def client_with_response(self, response):
        local, remote = socket.socketpair()
        local.settimeout(0.15 if response == 'timeout' else 2)
        client = RegistrationClient.__new__(RegistrationClient)
        client.sock = local
        client.parser = ET.XMLPullParser(events=['end'])
        client.parser.feed("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>")
        self.addCleanup(local.close)

        def server():
            try:
                incoming = b''
                while True:
                    incoming += remote.recv(4096)
                    try:
                        iq = ET.fromstring(incoming)
                        break
                    except ET.ParseError:
                        continue
                if response == 'timeout':
                    self.release.wait(2)
                elif response == 'stream':
                    remote.sendall(b"<stream:error><policy-violation xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>"
                                   b"<text xmlns='urn:ietf:params:xml:ns:xmpp-streams'>private-value</text></stream:error>")
                elif response != 'closed':
                    result = ET.Element('iq', {'xmlns': 'jabber:client', 'id': iq.get('id'),
                                               'type': 'result' if response == 'created' else 'error'})
                    if response != 'created':
                        error = ET.SubElement(result, 'error')
                        ET.SubElement(error, '{urn:ietf:params:xml:ns:xmpp-stanzas}not-allowed')
                    remote.sendall(ET.tostring(result))
            finally:
                remote.close()
        self.release = threading.Event()
        thread = threading.Thread(target=server, daemon=True)
        thread.start()
        self.addCleanup(lambda: (self.release.set(), thread.join(3)))
        return client

    def test_iq_rejection_checks_that_no_account_exists(self):
        checks = []
        check_plaintext_rejection(self.client_with_response('iq'), 'synthetic-user',
                                  lambda name: checks.append(name) or False)
        self.assertEqual(['synthetic-user'], checks)

    def test_stream_policy_rejection_is_accepted_with_backend_proof(self):
        check_plaintext_rejection(self.client_with_response('stream'), 'synthetic-user', lambda _: False)

    def test_stream_close_is_accepted_with_backend_proof(self):
        check_plaintext_rejection(self.client_with_response('closed'), 'synthetic-user', lambda _: False)

    def test_existing_account_does_not_turn_stream_error_into_false_pass(self):
        with self.assertRaisesRegex(RuntimeError, 'unexpectedly created'):
            check_plaintext_rejection(self.client_with_response('stream'), 'synthetic-user', lambda _: True)

    def test_timeout_is_not_treated_as_security_enforcement(self):
        with self.assertRaises(TimeoutError):
            check_plaintext_rejection(self.client_with_response('timeout'), 'synthetic-user', lambda _: False)

    def test_unencrypted_creation_fails_the_check(self):
        with self.assertRaisesRegex(RuntimeError, 'not rejected'):
            check_plaintext_rejection(self.client_with_response('created'), 'synthetic-user', lambda _: False)

    def test_stream_failure_keeps_server_text_out_of_error(self):
        with self.assertRaises(XmppStreamFailure) as caught:
            self.client_with_response('stream').create('synthetic-user', 'public-test-password')
        self.assertIn('policy-violation', str(caught.exception))
        self.assertNotIn('private-value', str(caught.exception))


if __name__ == '__main__':
    unittest.main()
