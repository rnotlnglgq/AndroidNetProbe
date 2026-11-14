#!/usr/bin/env python3
"""Create an ignored local research signing key. Never prints passwords or replaces keys."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
folder = root / '.signing'
config = root / 'release-signing.properties'
key = folder / 'netprobe-release.p12'
if config.exists() or key.exists():
    raise SystemExit('Signing configuration/key already exists; keep it for future updates.')
folder.mkdir(mode=0o700, exist_ok=True)
password = secrets.token_urlsafe(32)
env = dict(os.environ, NETPROBE_KEY_PASSWORD=password)
keytool = shutil.which('keytool')
if not keytool:
    raise SystemExit('Install a JDK and make keytool available on PATH.')
subprocess.run([keytool, '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12',
    '-alias', 'netprobe-research', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
    '-dname', 'CN=NetProbe Personal Research', '-storepass:env', 'NETPROBE_KEY_PASSWORD',
    '-keypass:env', 'NETPROBE_KEY_PASSWORD'], env=env, check=True, stdout=subprocess.DEVNULL)
key.chmod(0o600)
fd = os.open(config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as out:
    out.write('storeFile=.signing/netprobe-release.p12\nstorePassword=' + password +
        '\nkeyAlias=netprobe-research\nkeyPassword=' + password + '\n')
print('Local research release key created. Back up .signing/ and release-signing.properties securely.')
