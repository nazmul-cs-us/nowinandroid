#!/usr/bin/env bash
# Installs only the private Family module; preserves unrelated XMPP/coturn settings.
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo 'Run with sudo.' >&2; exit 1; }
source_dir=${1:?Usage: sudo bash install.sh /path/to/MongooseIM-6.10.0 [listener-IP]}
listener_ip=${2:-127.0.0.1}
release=/opt/mongooseim-6.10.0
root=$(cd "$(dirname "$0")" && pwd)
[ "$(git -C "$source_dir" rev-parse HEAD)" = 872cfdb8bc8512aa495a10ff54e52a3c5e09b5db ] || {
  echo 'Expected the pinned MongooseIM 6.10.0 source commit.' >&2; exit 1;
}
[ -f "$release/etc/vm.args" ] && [ -f /etc/mongooseim/mongooseim.toml ]
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
ERL_LIBS="$source_dir/_build/default/lib" erlc -I "$source_dir/include" -o "$build" "$root/mod_deenly_family"*.erl
python3 - "$listener_ip" <<'PY'
import datetime,grp,ipaddress,os,pathlib,re,shutil,sys,tempfile,tomllib
address=ipaddress.ip_address(sys.argv[1])
if address.version != 4 or not (address.is_loopback or address.is_private):
    raise SystemExit('Use loopback or the VM private IPv4 address, not a public/NAT address.')
config=pathlib.Path('/etc/mongooseim/mongooseim.toml')
text=config.read_text(); before=tomllib.loads(text)
listeners=before.get('listen',{}).get('c2s',[])
if len(listeners)!=1:
    raise SystemExit('Expected one c2s listener; preserve custom listeners with a manual config update.')
section=re.search(r'(?ms)^\[\[listen\.c2s\]\].*?(?=^\[|\Z)', text)
if section is None: raise SystemExit('Could not locate the c2s listener.')
old=section.group()
if not re.search(r'(?m)^ip_address\s*=',old): raise SystemExit('Expected an explicit c2s ip_address.')
new=re.sub(r'(?m)^ip_address\s*=.*$', 'ip_address = "'+str(address)+'"', old)
text=text[:section.start()]+new+text[section.end():]
if 'mod_deenly_family' not in before.get('modules',{}): text+='\n[modules.mod_deenly_family]\n'
after=tomllib.loads(text)
if after['listen']['c2s'][0]['tls']['mode']!='starttls_required':
    raise SystemExit('STARTTLS must remain mandatory.')
# Read the existing coturn secret locally. Never print or put it in the repository.
secrets=[]
for line in pathlib.Path('/etc/turnserver.conf').read_text().splitlines():
    m=re.match(r'^\s*static-auth-secret\s*=\s*(.*?)\s*$',line)
    if m: secrets.append(m.group(1))
if len(secrets)!=1 or not secrets[0]: raise SystemExit('Expected exactly one nonempty static-auth-secret.')
gid=grp.getgrnam('mongooseim').gr_gid
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
backup=config.with_name(config.name+'.before-family-'+stamp)
shutil.copy2(config,backup);os.chown(backup,0,0);os.chmod(backup,0o600)
for path,value in [(pathlib.Path('/etc/mongooseim/turn-secret'),secrets[0]+'\n'),(config,text)]:
    fd,tmp=tempfile.mkstemp(dir=path.parent)
    with os.fdopen(fd,'w') as out: out.write(value)
    os.chown(tmp,0,gid);os.chmod(tmp,0o640);os.replace(tmp,path)
print('Family config and private TURN issuer secret installed. Backup:',backup)
PY
beam="$release/lib/mongooseim-6.10.0/ebin/mod_deenly_family.beam"
if [ -f "$beam" ]; then cp -p "$beam" "$beam.before-family-$(date -u +%Y%m%dT%H%M%S%N)"; fi
install -o root -g mongooseim -m 0640 "$build/mod_deenly_family.beam" "$beam"
install -o root -g mongooseim -m 0640 "$build/mod_deenly_family_push.beam" "$(dirname "$beam")/mod_deenly_family_push.beam"
install -o root -g mongooseim -m 0640 "$build/mod_deenly_family_presence.beam" "$(dirname "$beam")/mod_deenly_family_presence.beam"
install -o root -g mongooseim -m 0640 "$build/mod_deenly_family_web.beam" "$(dirname "$beam")/mod_deenly_family_web.beam"
systemctl restart mongooseim
for attempt in {1..20}; do
  if /usr/local/bin/mongooseimctl status >/dev/null 2>&1; then
    echo 'MongooseIM Family module started.'
    /usr/local/bin/mongooseimctl status
    exit 0
  fi
  sleep 2
done
echo 'Startup did not pass. Inspect mongooseim logs locally; do not share credentials or full configuration.' >&2
exit 1
