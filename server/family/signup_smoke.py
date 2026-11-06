#!/usr/bin/env python3
"""Live create-only signup acceptance check. Run as mongooseim; removes its test accounts."""
import secrets
import socket
import ssl
import uuid
import xml.etree.ElementTree as ET
from smoke import Client, DOMAIN, CLIENT, STREAM, account, connection_target, require

REGISTER = '{jabber:iq:register}'
ERROR = '{urn:ietf:params:xml:ns:xmpp-stanzas}'


class RegistrationClient(Client):
    def __init__(self, endpoint, secure=True):
        self.sock = socket.create_connection(endpoint, timeout=15)
        try:
            self.open_stream()
            if secure:
                self.send("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")
                self.wait(lambda e: e.tag == '{urn:ietf:params:xml:ns:xmpp-tls}proceed')
                self.sock = ssl.create_default_context().wrap_socket(self.sock, server_hostname=DOMAIN)
                self.open_stream()
        except BaseException:
            self.sock.close()
            raise

    def create(self, username, password):
        request_id = str(uuid.uuid4())
        iq = ET.Element('iq', {'type': 'set', 'to': DOMAIN, 'id': request_id})
        query = ET.SubElement(iq, 'query', {'xmlns': 'jabber:iq:register'})
        ET.SubElement(query, 'username').text = username
        ET.SubElement(query, 'password').text = password
        self.send(ET.tostring(iq, encoding='unicode'))
        response = self.wait(lambda e: e.tag == CLIENT + 'iq' and e.get('id') == request_id)
        if response.get('type') == 'result':
            return 'created'
        error = response.find(CLIENT + 'error')
        return next((element.tag[len(ERROR):] for element in (error if error is not None else []) if element.tag.startswith(ERROR)), 'unknown')


def main():
    endpoint = connection_target()
    names = ['signup-smoke-' + secrets.token_hex(6) for _ in range(3)]
    names += ['weak-' + secrets.token_hex(3), 'limited-' + secrets.token_hex(3)]
    passwords = [secrets.token_urlsafe(24) for _ in range(2)]
    clients, authenticated = [], []
    try:
        plain = RegistrationClient(endpoint, secure=False)
        clients.append(plain)
        # Public synthetic value only: never submit a real password before TLS.
        require(plain.create(names[2], 'public-diagnostic-placeholder-123') == 'not-allowed',
                'Signup was not rejected before TLS.')
        print('PASS: account creation rejected before TLS.')
        registration = RegistrationClient(endpoint)
        clients.append(registration)
        for name, password in zip(names, passwords):
            require(registration.create(name, password) == 'created', 'Verified-TLS signup failed (check rollout or rate limit).')
        require(registration.create(names[0], secrets.token_urlsafe(24)) == 'conflict',
                'Duplicate signup did not preserve the existing account.')
        reserved_result = registration.create('admin', passwords[0])
        if reserved_result == 'created':
            names.append('admin')  # Clean up only if this test actually created it.
        require(reserved_result == 'forbidden', 'Reserved username was not rejected.')
        require(registration.create(names[3], 'short') == 'not-acceptable',
                'Weak password was not rejected.')
        require(registration.create(names[4], passwords[0]) == 'resource-constraint',
                'Signup attempt limit was not enforced.')
        print('PASS: signup, duplicate protection, reserved names, password rules and rate limiting.')
        for name, password in zip(names, passwords):
            client = Client(name, password, endpoint)
            authenticated.append(client); clients.append(client)
        first, second = authenticated
        require(first.request('snapshot')['members'] == [], 'New account unexpectedly has family members.')
        call_id = str(uuid.uuid4())
        first.request('invite_call', expected_error='not_family', peer=second.jid, callId=call_id, kind='voice')
        first.request('invite', peer=second.jid)
        second.request('accept_member', peer=first.jid)
        first.request('invite_call', peer=second.jid, callId=call_id, kind='voice')
        first.request('end_call', callId=call_id)
        print('PASS: new accounts authenticate; calling still requires invitation acceptance.')
    finally:
        if len(authenticated) == 2:
            try:
                authenticated[0].request('remove_member', peer=authenticated[1].jid)
            except RuntimeError:
                pass
        for client in clients:
            client.sock.close()
        for name in names:
            # Names are unique to this run. Removing a non-created account may return an error.
            try:
                account('removeUser', user=name + '@' + DOMAIN)
            except RuntimeError:
                pass
        print('Temporary signup account cleanup attempted.')


if __name__ == '__main__':
    main()
