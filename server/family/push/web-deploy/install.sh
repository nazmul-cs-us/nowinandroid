#!/usr/bin/env bash
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo 'Run with sudo.' >&2; exit 1; }
origin=${1:-https://xmpp.starception.com:8443}
root=$(cd "$(dirname "$0")/.." && pwd)
# Uses a separate TLS port, preserving the existing certbot/HTTP and XMPP listeners.
python3 - "$origin" <<'PY'
import sys
from urllib.parse import urlsplit
p=urlsplit(sys.argv[1])
if p.scheme!='https' or p.hostname!='xmpp.starception.com' or p.port!=8443 or p.path not in ('','/') or p.username or p.password or p.query or p.fragment:
    raise SystemExit('Use https://xmpp.starception.com:8443 for this VM.')
PY
for cert in /etc/mongooseim/certs/fullchain.pem /etc/mongooseim/certs/privkey.pem; do
  sudo -u mongooseim test -r "$cert" || { echo 'The existing MongooseIM TLS files must be service-readable.' >&2; exit 1; }
done
bash "$root/install.sh"
/opt/deenly-family-push/venv/bin/python - "$origin" <<'PY'
import base64, grp, json, os, pathlib, sys, tempfile
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
keyfile=pathlib.Path('/etc/mongooseim/family-web-vapid.pem')
gid=grp.getgrnam('mongooseim').gr_gid
if keyfile.exists():
    key=serialization.load_pem_private_key(keyfile.read_bytes(),password=None)
else:
    key=ec.generate_private_key(ec.SECP256R1())
    fd=os.open(keyfile,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o640)
    with os.fdopen(fd,'wb') as output:
        output.write(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    os.chown(keyfile,0,gid)
public=key.public_key().public_bytes(serialization.Encoding.X962,serialization.PublicFormat.UncompressedPoint)
origin=sys.argv[1].rstrip('/')
config={'url':origin+'/family-helper/','domain':'xmpp.starception.com',
        'publicKey':base64.urlsafe_b64encode(public).rstrip(b'=').decode(),
        'privateKey':str(keyfile),'subject':'https://xmpp.starception.com'}
fd,path=tempfile.mkstemp(dir='/etc/mongooseim')
with os.fdopen(fd,'w') as out: json.dump(config,out)
os.chown(path,0,gid);os.chmod(path,0o640)
os.replace(path,'/etc/mongooseim/family-web.json')
print('Temporary helper configured at '+config['url']+' (private keys withheld).')
PY
install -m 0644 "$root/web-deploy/deenly-family-web.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now deenly-family-push deenly-family-web
systemctl restart deenly-family-push deenly-family-web
echo 'Services started. Allow TCP 8443 for the test phones, then open the HTTPS helper.'
