#!/usr/bin/env python3
"""Local release validation and packaging; publishing belongs to the Actions publish job."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
UNIVERSAL_ABIS = ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')
DISTRIBUTIONS = {'arm64-v8a': ('arm64-v8a',), 'armeabi-v7a': ('armeabi-v7a',), 'universal': UNIVERSAL_ABIS}
SEMVER = r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'


def run(*args, cwd=ROOT):
    return subprocess.run([str(arg) for arg in args], cwd=cwd, check=True,
                          capture_output=True, text=True).stdout.strip()


def versions(text):
    names = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    codes = re.findall(r'^\s*versionCode\s*=\s*([0-9]+)\s*$', text, re.M)
    if len(names) != 1 or len(codes) != 1 or not re.fullmatch(SEMVER, names[0]):
        raise ValueError('Expected exactly one explicit stable versionName and versionCode')
    code = int(codes[0])
    if code < 1:
        raise ValueError('versionCode must be positive')
    return names[0], code


def tag_version(tag):
    if not re.fullmatch('v' + SEMVER, tag):
        raise ValueError('Release tags must have the form vX.Y.Z')
    return tag[1:]


def validate_tag(tag, main_ref='origin/main', root=ROOT):
    version = tag_version(tag)
    name, code = versions((root / 'app/build.gradle.kts').read_text())
    if version != name:
        raise ValueError(f'Tag {tag} does not match versionName {name}')
    commit = run('git', 'rev-parse', '--verify', f'refs/tags/{tag}^{{commit}}', cwd=root)
    if commit != run('git', 'rev-parse', 'HEAD', cwd=root):
        raise ValueError('Checkout must be the tagged commit')
    run('git', 'merge-base', '--is-ancestor', commit, main_ref, cwd=root)
    previous = run('git', 'tag', '--merged', commit, '--list', 'v*', cwd=root).splitlines()
    for old_tag in previous:
        if old_tag == tag or not re.fullmatch('v' + SEMVER, old_tag):
            continue
        old_name, old_code = versions(run('git', 'show', f'refs/tags/{old_tag}:app/build.gradle.kts', cwd=root))
        if old_name != tag_version(old_tag):
            raise ValueError(f'Existing version tag has inconsistent metadata: {old_tag}')
        if tuple(map(int, version.split('.'))) <= tuple(map(int, old_name.split('.'))) or code <= old_code:
            raise ValueError(f'Increase versionName and versionCode after {old_tag}')
    return {'tag': tag, 'versionName': name, 'versionCode': code, 'gitCommit': commit}


def android_sdk(root=ROOT):
    configured = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if configured:
        return Path(configured)
    local = root / 'local.properties'
    match = re.search(r'^sdk.dir=(.+)$', local.read_text(), re.M) if local.exists() else None
    if not match:
        raise ValueError('Configure ANDROID_HOME or local.properties')
    return Path(match.group(1).strip())


def apk_metadata(text):
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", text, re.M)
    if not match:
        raise ValueError('Missing APK package metadata')
    return {'applicationId': match.group(1), 'versionCode': int(match.group(2)),
            'versionName': match.group(3), 'debuggable': bool(re.search(r'^application-debuggable\b', text, re.M))}


def certificate(text):
    found = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', text, re.M)
    if len(found) != 1:
        raise ValueError('Expected exactly one APK signer')
    return found[0].lower()


def distribution_metadata(apk, badging, distribution="universal"):
    def integer(key):
        match = re.search(r"^" + key + r":'([0-9]+)'$", badging, re.M)
        if not match:
            raise ValueError(f'Missing APK SDK metadata: {key}')
        return int(match.group(1))
    label = re.search(r"^application-label:'([^']+)'$", badging, re.M)
    if not label or label.group(1) != 'NetProbe':
        raise ValueError('Expected application label NetProbe')
    with zipfile.ZipFile(apk) as archive:
        abis = sorted(name.split('/')[1] for name in archive.namelist()
                      if re.fullmatch(r'lib/[^/]+/libnetprobe_bionic\.so', name))
    if distribution not in DISTRIBUTIONS or tuple(abis) != DISTRIBUTIONS[distribution]:
        raise ValueError(f'Expected {distribution} native ABIs {DISTRIBUTIONS.get(distribution)}: {abis}')
    with zipfile.ZipFile(apk) as archive:
        packaged = {name.split("/")[1] for name in archive.namelist() if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name)}
    if packaged != set(DISTRIBUTIONS[distribution]):
        raise ValueError(f'Unexpected packaged native ABIs: {packaged}')
    return {'applicationLabel': label.group(1), 'minSdk': integer('minSdkVersion'),
            'targetSdk': integer('targetSdkVersion'), 'distribution': distribution, 'abis': abis}


def verify_apk(apk, debug=False, root=ROOT, distribution="universal"):
    apk = Path(apk).resolve()
    tools = android_sdk(root) / 'build-tools/36.0.0'
    signature = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    badging = run(tools / 'aapt2', 'dump', 'badging', apk)
    info = apk_metadata(badging)
    expected_name, expected_code = versions((root / 'app/build.gradle.kts').read_text())
    if info != {'applicationId': 'com.rnotlnglgq.netprobe', 'versionCode': expected_code,
                'versionName': expected_name, 'debuggable': debug}:
        raise ValueError(f'Unexpected APK metadata: {info}')
    fingerprint = certificate(signature)
    if not debug and fingerprint != (root / 'signing/certificate-sha256.txt').read_text().strip().lower():
        raise ValueError('Release signing certificate differs from the shared identity')
    run(tools / 'zipalign', '-c', '-P', '16', '4', apk)
    distribution_info = distribution_metadata(apk, badging, distribution)
    gradle = (root / 'app/build.gradle.kts').read_text()
    for key in ('minSdk', 'targetSdk'):
        configured = re.findall(r'^\s*' + key + r'\s*=\s*([0-9]+)\s*$', gradle, re.M)
        if len(configured) != 1 or distribution_info[key] != int(configured[0]):
            raise ValueError(f'APK {key} differs from Gradle configuration')
    return {**info, **distribution_info, 'variant': 'debug' if debug else 'release',
            'certificateSha256': fingerprint, 'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest()}


def package_release(tag, root=ROOT, main_ref='origin/main'):
    validated = validate_tag(tag, main_ref=main_ref, root=root)
    sources = {kind: root / f'app/build/distributions/{kind}/app-release.apk' for kind in DISTRIBUTIONS}
    verified = {kind: verify_apk(path, root=root, distribution=kind) for kind, path in sources.items()}
    out = root / 'dist'
    if out.exists():
        raise ValueError('dist already exists; use a fresh workspace or move previous output')
    out.mkdir()
    files = []
    apks = []
    for kind, source in sources.items():
        destination = out / f'NetProbe-{validated["versionName"]}-{kind}.apk'
        shutil.copyfile(source, destination)
        files.append(destination)
        apks.append({**verified[kind], 'apkFile': destination.name})
    jdk = subprocess.run(['java', '-version'], check=True, capture_output=True, text=True).stderr.splitlines()[0]
    metadata = {**validated, 'apks': apks, 'signingPolicy': 'public-shared-project-key',
                'toolchain': {'jdkLauncherRuntime': jdk, 'gradleDaemonJavaMajor': '25', 'gradle': '9.6.0', 'agp': '9.4.1', 'sdk': '37.0',
                              'buildTools': '36.0.0', 'ndk': '25.1.8937393', 'cmake': '3.22.1'}}
    (out / 'build-info.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
    files.append(out / 'build-info.json')
    (out / 'SHA256SUMS').write_text(''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in files))
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    check = commands.add_parser('validate-tag')
    check.add_argument('--tag', required=True)
    check.add_argument('--main-ref', default='origin/main')
    verify = commands.add_parser('verify-apk')
    verify.add_argument('--apk', required=True, type=Path)
    verify.add_argument('--debug', action='store_true')
    verify.add_argument('--distribution', choices=DISTRIBUTIONS, default='universal')
    package = commands.add_parser('package')
    package.add_argument('--tag', required=True)
    package.add_argument('--main-ref', default='origin/main')
    args = parser.parse_args()
    try:
        if args.command == 'validate-tag': result = validate_tag(args.tag, args.main_ref)
        elif args.command == 'verify-apk': result = verify_apk(args.apk, args.debug, distribution=args.distribution)
        else: result = package_release(args.tag, main_ref=args.main_ref)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f'Release check failed: {error}') from error
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
