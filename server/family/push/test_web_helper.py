import base64
import hashlib
import hmac
import http.client
import json
from pathlib import Path
import sqlite3
import tempfile
import datetime
import ssl
import threading
import time
import unittest
from http.server import ThreadingHTTPServer
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
import web_helper
import web_check
import shutil

SECRET = 'synthetic-web-pair-secret-01234567890123456789'
ACCOUNT = 'bob@xmpp.starception.com'
OWNER = '11111111-2222-3333-4444-555555555555'

def b64(value): return base64.urlsafe_b64encode(value).rstrip(b'=').decode()

def code(**fields):
    value = dict(purpose='family-web-pair-v1', account=ACCOUNT, owner=OWNER, generation=0,
                 expires=int(time.time())+300, nonce=b64(__import__('secrets').token_bytes(24)))
    value.update(fields)
    encoded = b64(json.dumps(value).encode())
    return encoded+'.'+b64(hmac.new(SECRET.encode(), ('family-web-pair-v1:'+encoded).encode(), hashlib.sha256).digest())

def subscription(endpoint='https://web.push.apple.com/synthetic-subscription'):
    key = ec.generate_private_key(ec.SECP256R1()).public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
    return dict(endpoint=endpoint, keys=dict(p256dh=b64(key), auth=b64(b'synthetic-auth16')))

class StoreTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.store=web_helper.Store(Path(self.temp.name)/'alerts.sqlite', SECRET)
    def tearDown(self): self.temp.cleanup()
    def test_pairing_is_one_use_and_cannot_change_account(self):
        token=code()
        result=self.store.pair(token, subscription())
        self.assertGreater(result['expires'], int(time.time()))
        self.assertEqual(1, len(self.store.targets(ACCOUNT, {OWNER:0})))
        self.assertEqual([], self.store.targets('alice@xmpp.starception.com', {OWNER:0}))
        with self.assertRaises(sqlite3.IntegrityError): self.store.pair(token, subscription())
        changed=token.split('.')[0]+'x.'+token.split('.')[1]
        with self.assertRaises(ValueError): self.store.pair(changed, subscription())
    def test_expiry_and_foreign_accounts_rejected(self):
        for token in [code(expires=int(time.time())-1), code(expires=int(time.time())+301), code(account='bob@other.test')]:
            with self.assertRaises(ValueError): self.store.pair(token, subscription())
    def test_endpoints_cannot_target_local_or_arbitrary_servers(self):
        for endpoint in ['http://web.push.apple.com/path','https://127.0.0.1/private','https://web.push.apple.com.attacker.test/push',
                         'https://user:pass@web.push.apple.com/push','https://web.push.apple.com:8443/push']:
            with self.assertRaises(ValueError): self.store.pair(code(), subscription(endpoint))
    def test_logout_generation_revokes_old_helpers(self):
        self.store.pair(code(), subscription())
        self.assertEqual([], self.store.targets(ACCOUNT, {OWNER:1}))
        self.assertEqual([], self.store.targets(ACCOUNT, {OWNER:0}))
    def test_revoke_requires_the_returned_secret(self):
        result=self.store.pair(code(), subscription())
        with self.assertRaises(ValueError): self.store.revoke(result['id'], 'wrong')
        self.assertEqual(1,len(self.store.targets(ACCOUNT,{OWNER:0})))
        self.store.revoke(result['id'], result['revoke'])
        self.assertEqual([],self.store.targets(ACCOUNT,{OWNER:0}))
    def test_expired_helpers_are_removed(self):
        result=self.store.pair(code(), subscription())
        with self.store.connect() as db: db.execute('UPDATE subscriptions SET expires=0 WHERE id=?',(result['id'],))
        self.assertEqual([],self.store.targets(ACCOUNT,{OWNER:0}))
    def test_repair_changes_endpoint_ownership_without_duplicates(self):
        self.store.pair(code(),subscription())
        self.store.pair(code(account='alice@xmpp.starception.com'),subscription())
        self.assertEqual([],self.store.targets(ACCOUNT,{OWNER:0}))
        self.assertEqual(1,len(self.store.targets('alice@xmpp.starception.com',{OWNER:0})))
    def test_helper_count_is_bounded(self):
        for i in range(8): self.store.pair(code(),subscription(f'https://web.push.apple.com/endpoint-{i}'))
        with self.assertRaises(ValueError): self.store.pair(code(),subscription('https://web.push.apple.com/extra'))
    def test_delivery_uses_authorized_targets_and_keeps_payload_minimal(self):
        self.store.pair(code(), subscription())
        sent=[]
        def sender(**request): sent.append(request)
        payload={'callId':'11111111-2222-3333-4444-555555555555','callee':ACCOUNT,'caller':'alice@xmpp.starception.com',
                 'kind':'video','expires':int(time.time()*1000)+40000}
        config={'privateKey':'synthetic-private-key-path','subject':'https://xmpp.starception.com'}
        registration={'token':ACCOUNT,'generations':{OWNER:0}}
        self.assertEqual(200,web_helper.deliver(registration,payload,settings=config,store=self.store,sender=sender))
        self.assertEqual(1,len(sent))
        body=json.loads(sent[0]['data'])
        self.assertEqual({'v','callId','kind','expires'},set(body))
        self.assertLessEqual(sent[0]['ttl'],45)
        self.assertEqual('high',sent[0]['headers']['Urgency'])
        sent.clear();registration['generations'][OWNER]=1
        self.assertEqual(200,web_helper.deliver(registration,payload,settings=config,store=self.store,sender=sender))
        self.assertEqual([],sent)
    def test_key_material_is_validated_before_storing(self):
        value=subscription();value['keys']['auth']=b64(b'short')
        with self.assertRaises(ValueError): self.store.pair(code(),value)

class ProviderTests(unittest.TestCase):
    def test_generated_pem_key_encrypts_with_real_webpush_library(self):
        import requests
        from pywebpush import webpush
        class Adapter(requests.adapters.BaseAdapter):
            def __init__(self): self.sent=[]
            def send(self,request,**kwargs):
                self.sent.append(request)
                response=requests.Response();response.status_code=201;response._content=b'';response.request=request
                return response
            def close(self): pass
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'vapid.pem'
            key=ec.generate_private_key(ec.SECP256R1())
            path.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
            adapter=Adapter()
            with web_helper.ProviderSession() as session:
                session.mount('https://',adapter)
                webpush(subscription_info=subscription(),data='synthetic-family-alert',vapid_private_key=str(path),
                    vapid_claims={'sub':'https://xmpp.starception.com'},ttl=30,timeout=3,requests_session=session)
            self.assertEqual(1,len(adapter.sent))
            self.assertEqual('aes128gcm',adapter.sent[0].headers['Content-Encoding'])
            self.assertTrue(adapter.sent[0].headers['Authorization'].startswith('vapid '))
            self.assertNotIn(b'synthetic-family-alert',adapter.sent[0].body)
    def test_provider_redirect_cannot_reach_an_untrusted_server(self):
        import requests
        class Adapter(requests.adapters.BaseAdapter):
            def __init__(self): self.urls=[]
            def send(self,request,**kwargs):
                self.urls.append(request.url)
                response=requests.Response();response.status_code=302
                response.headers['Location']='https://127.0.0.1/private'
                response._content=b'';response.request=request
                return response
            def close(self): pass
        adapter=Adapter()
        with web_helper.ProviderSession() as session:
            session.mount('https://',adapter)
            response=session.post('https://web.push.apple.com/synthetic')
            self.assertEqual(302,response.status_code)
            self.assertEqual(['https://web.push.apple.com/synthetic'],adapter.urls)
            with self.assertRaises(ValueError): session.post('https://127.0.0.1/private')

class HTTPTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.server=ThreadingHTTPServer(('127.0.0.1',0),web_helper.Handler)
        self.server.settings={'url':'https://xmpp.starception.com:8443/family-helper/','publicKey':'synthetic-public-key'}
        self.server.store=web_helper.Store(Path(self.temp.name)/'alerts.sqlite',SECRET)
        self.server.static=Path(__file__).with_name('web')
        self.server.allow=lambda _:True
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def test_origin_validation_blocks_cross_site_pairing(self):
        client=http.client.HTTPConnection(*self.server.server_address,timeout=2)
        client.request('POST','/family-helper/api/subscribe',json.dumps({'code':code(),'subscription':subscription()}),
                       {'Origin':'https://other.test','Content-Type':'application/json'})
        response=client.getresponse();self.assertEqual(403,response.status);response.read();client.close()
    def test_public_key_and_static_page_do_not_contain_pairing_secrets(self):
        client=http.client.HTTPConnection(*self.server.server_address,timeout=2)
        client.request('GET','/family-helper/api/key')
        response=client.getresponse();self.assertEqual(200,response.status)
        self.assertEqual({'publicKey':'synthetic-public-key'},json.loads(response.read()))
        client.close()
        client=http.client.HTTPConnection(*self.server.server_address,timeout=2)
        client.request('GET','/family-helper/')
        response=client.getresponse();self.assertEqual(200,response.status)
        self.assertIn('frame-ancestors',response.getheader('Content-Security-Policy'))
        self.assertNotIn(SECRET.encode(),response.read());client.close()
    def test_path_traversal_does_not_serve_files(self):
        client=http.client.HTTPConnection(*self.server.server_address,timeout=2)
        client.request('GET','/family-helper/../../bridge.py')
        response=client.getresponse();self.assertEqual(404,response.status);response.read();client.close()



class TLSTests(unittest.TestCase):
    def setUp(self):
        from cryptography import x509
        from cryptography.x509.oid import NameOID
        from cryptography.hazmat.primitives import hashes
        self.temp = tempfile.TemporaryDirectory()
        folder = Path(self.temp.name)
        key = ec.generate_private_key(ec.SECP256R1())
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'localhost')])
        now = datetime.datetime.now(datetime.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key())
                .serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=1))
                .not_valid_after(now+datetime.timedelta(days=1)).add_extension(
                    x509.SubjectAlternativeName([x509.DNSName('localhost')]), critical=False)
                .sign(key, hashes.SHA256()))
        self.certfile = folder / 'cert.pem'
        keyfile = folder / 'key.pem'
        self.certfile.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        keyfile.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                            serialization.NoEncryption()))
        self.settings = {'url': 'https://localhost/family-helper/', 'publicKey': 'synthetic-public-key',
                         'certificate': str(self.certfile), 'certificateKey': str(keyfile)}
        store = web_helper.Store(folder / 'alerts.sqlite', SECRET)
        self.server = web_helper.Server(('127.0.0.1', 0), self.settings, store)
        self.settings['url'] = f'https://localhost:{self.server.server_address[1]}/family-helper/'
        self.server.static = folder / 'web'
        shutil.copytree(Path(web_helper.__file__).with_name('web'), self.server.static)
        icon = Path(__file__).resolve().parents[3] / 'iosApp/Sources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png'
        shutil.copyfile(icon, self.server.static / 'icon.png')
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.temp.cleanup()

    def context(self):
        return ssl.create_default_context(cafile=str(self.certfile))

    def test_public_helper_serves_over_verified_tls(self):
        client = http.client.HTTPSConnection('localhost', self.server.server_address[1], timeout=3,
                                            context=self.context())
        client.request('GET', '/family-helper/api/key')
        response = client.getresponse()
        self.assertEqual(200, response.status)
        self.assertEqual('synthetic-public-key', json.loads(response.read())['publicKey'])
        client.close()

    def test_readiness_checks_actual_install_assets_and_key(self):
        web_check.probe(self.settings, context=self.context())
        with self.assertRaises(ValueError):
            web_check.probe({**self.settings, 'publicKey': 'stale-key'}, context=self.context())
        (self.server.static / 'manifest.webmanifest').write_text(
            json.dumps({'display': 'browser', 'scope': '/family-helper/'}))
        with self.assertRaises(ValueError):
            web_check.probe(self.settings, context=self.context())

    def test_readiness_rejects_untrusted_tls_certificate(self):
        with self.assertRaises(ssl.SSLCertVerificationError):
            web_check.probe(self.settings)

    def test_readiness_verifies_public_hostname_while_connecting_to_loopback(self):
        settings = {**self.settings, 'url': self.settings['url'].replace('localhost', 'wrong.test')}
        with self.assertRaises(ssl.SSLCertVerificationError):
            web_check.probe(settings, context=self.context())

if __name__=='__main__': unittest.main()
