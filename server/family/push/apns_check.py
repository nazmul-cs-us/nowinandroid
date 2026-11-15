#!/usr/bin/env python3
"""Read-only APNs configuration check; no tokens, keys or credentials are printed."""
import argparse
import re
import shlex
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec


def read_environment(path):
    settings = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, separator, value = line.partition("=")
        if not separator:
            raise ValueError("Invalid environment file")
        # Parse quoted EnvironmentFile values without executing shell expressions.
        words = shlex.split(value, comments=True)
        if len(words) > 1:
            raise ValueError("Invalid environment value")
        settings[name.strip()] = words[0] if words else ""
    return settings


def check_apns_config(settings):
    issues = []
    for name in ("FAMILY_APNS_TEAM_ID", "FAMILY_APNS_KEY_ID"):
        if not re.fullmatch(r"[A-Z0-9]{10}", settings.get(name, "")):
            issues.append(f"Set {name} to the 10-character Apple identifier.")
    bundle = settings.get("FAMILY_APNS_BUNDLE_ID", "com.starception.submission")
    if not re.fullmatch(r"[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+", bundle):
        issues.append("Set FAMILY_APNS_BUNDLE_ID to the signed app's bundle identifier.")
    key_path = settings.get("FAMILY_APNS_KEY_FILE", "")
    if not key_path:
        issues.append("Set FAMILY_APNS_KEY_FILE to the installed Apple APNs .p8 key.")
    else:
        try:
            key = serialization.load_pem_private_key(Path(key_path).read_bytes(), password=None)
            if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
                issues.append("The APNs key must be an EC P-256 private key.")
        except (OSError, ValueError, TypeError):
            issues.append("The APNs key could not be read as an unencrypted private key.")
    return issues


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env", default="/etc/mongooseim/family-push.env")
    args = parser.parse_args()
    try:
        settings = read_environment(args.env)
    except (OSError, ValueError):
        print("APNs configuration could not be read. Run on the VM with access to family-push.env.")
        return 1
    issues = check_apns_config(settings)
    for issue in issues:
        print("NEEDS SETUP:", issue)
    if issues:
        return 1
    print("PASS: Apple identifiers, bundle identifier and APNs signing key are configured.")
    print("NEXT: build with project-push.yml and test on a signed iPhone with Deenly closed.")
    print("This local check does not verify Apple account entitlements, provider acceptance or delivery.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
