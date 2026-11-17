import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'release.py'
spec = importlib.util.spec_from_file_location('netprobe_release', MODULE)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def config(version, code):
    return f'android {{\n defaultConfig {{\n versionName = "{version}"\n versionCode = {code}\n }}\n}}\n'


class VersionTest(unittest.TestCase):
    def test_explicit_stable_versions(self):
        self.assertEqual(('0.1.0', 9), release.versions(config('0.1.0', 9)))
        for value in ['v1.0.0-rc1', 'v01.0.0', '1.0.0', 'v1.0.0;echo bad', 'v1.0.0\n']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                release.tag_version(value)
        self.assertEqual('1.2.3', release.tag_version('v1.2.3'))

    def test_ambiguous_or_derived_version_is_rejected(self):
        for text in [config('0.1.0', 0), config('0.1.0', 9) + '\nversionCode = 10\n',
                     'versionName = providers.gradleProperty("version")\nversionCode = 1', config('01.0.0', 1)]:
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.versions(text)

    def test_apk_metadata_and_single_signer(self):
        parsed = release.apk_metadata("package: name='com.example' versionCode='9' versionName='0.1.0'\napplication-debuggable\n")
        self.assertTrue(parsed['debuggable'])
        self.assertEqual(9, parsed['versionCode'])
        self.assertEqual('a' * 64, release.certificate('Signer #1 certificate SHA-256 digest: ' + 'a' * 64))
        with self.assertRaises(ValueError):
            release.certificate('Signer #1 certificate SHA-256 digest: ' + 'a' * 64 + '\nSigner #2 certificate SHA-256 digest: ' + 'b' * 64)


class DistributionTest(unittest.TestCase):
    def test_sdk_label_and_all_abis_are_recorded(self):
        with tempfile.TemporaryDirectory(prefix='netprobe-distribution-test-') as directory:
            apk = Path(directory) / 'test.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                for abi in release.UNIVERSAL_ABIS:
                    archive.writestr(f'lib/{abi}/libnetprobe_bionic.so', b'fixture')
            badging = "application-label:'NetProbe'\nminSdkVersion:'26'\ntargetSdkVersion:'37'\n"
            info = release.distribution_metadata(apk, badging)
            self.assertEqual('universal', info['distribution'])
            self.assertEqual(26, info['minSdk'])
            self.assertEqual(37, info['targetSdk'])
            self.assertEqual(list(release.UNIVERSAL_ABIS), info['abis'])
            for invalid in (badging.replace('NetProbe', 'Other'), badging.replace("minSdkVersion:'26'", '')):
                with self.assertRaises(ValueError):
                    release.distribution_metadata(apk, invalid)

    def test_arm_packages_have_exact_abi_and_reject_mislabeled_artifacts(self):
        with tempfile.TemporaryDirectory(prefix='netprobe-distribution-test-') as directory:
            apk = Path(directory) / 'test.apk'
            badging = "application-label:'NetProbe'\nminSdkVersion:'26'\ntargetSdkVersion:'37'\n"
            for kind in ('arm64-v8a', 'armeabi-v7a'):
                with zipfile.ZipFile(apk, 'w') as archive:
                    archive.writestr(f'lib/{kind}/libnetprobe_bionic.so', b'fixture')
                self.assertEqual([kind], release.distribution_metadata(apk, badging, kind)['abis'])
                with self.assertRaises(ValueError):
                    release.distribution_metadata(apk, badging, 'universal')
                with zipfile.ZipFile(apk, 'a') as archive:
                    archive.writestr('lib/x86/libdependency.so', b'fixture')
                with self.assertRaisesRegex(ValueError, 'packaged native ABIs'):
                    release.distribution_metadata(apk, badging, kind)

    def test_incomplete_native_distribution_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix='netprobe-distribution-test-') as directory:
            apk = Path(directory) / 'test.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/arm64-v8a/libnetprobe_bionic.so', b'fixture')
            with self.assertRaisesRegex(ValueError, 'native ABIs'):
                release.distribution_metadata(apk, "application-label:'NetProbe'\nminSdkVersion:'26'\ntargetSdkVersion:'37'\n")


class TagTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='netprobe-release-test-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.git('init', '-b', 'main')
        self.file = self.root / 'app/build.gradle.kts'
        self.file.parent.mkdir()

    def git(self, *args):
        return release.run('git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', *args, cwd=self.root)

    def commit(self, version, code):
        self.file.write_text(config(version, code))
        self.git('add', '.')
        self.git('commit', '-m', 'version')

    def test_annotated_tag_matches_main_and_allows_increasing_code(self):
        self.commit('0.1.0', 9)
        self.git('tag', '-a', 'v0.1.0', '-m', 'first')
        self.assertEqual(9, release.validate_tag('v0.1.0', 'main', self.root)['versionCode'])
        self.commit('0.1.1', 10)
        self.git('tag', 'v0.1.1')
        self.assertEqual('0.1.1', release.validate_tag('v0.1.1', 'main', self.root)['versionName'])

    def test_other_branch_tag_is_rejected(self):
        self.commit('0.1.0', 9)
        self.git('switch', '-c', 'outside')
        self.commit('0.2.0', 10)
        self.git('tag', 'v0.2.0')
        with self.assertRaises(subprocess.CalledProcessError):
            release.validate_tag('v0.2.0', 'main', self.root)

    def test_wrong_tag_checkout_and_reused_code_are_rejected(self):
        self.commit('0.1.0', 9)
        self.git('tag', 'v0.1.0')
        self.git('tag', 'v0.9.0')
        with self.assertRaises(ValueError):
            release.validate_tag('v0.9.0', 'main', self.root)
        self.commit('0.1.1', 9)
        with self.assertRaises(ValueError):
            release.validate_tag('v0.1.0', 'main', self.root)
        self.git('tag', 'v0.1.1')
        with self.assertRaises(ValueError):
            release.validate_tag('v0.1.1', 'main', self.root)


class PackageTest(unittest.TestCase):
    def test_assets_and_metadata_hashes_and_repeated_output(self):
        with tempfile.TemporaryDirectory(prefix='netprobe-package-test-') as directory:
            root = Path(directory)
            source = root / 'app/build/distributions/universal/app-release.apk'
            for kind in release.DISTRIBUTIONS:
                path = root / f'app/build/distributions/{kind}/app-release.apk'
                path.parent.mkdir(parents=True)
                path.write_bytes(b'synthetic apk fixture')
            validation = {'tag': 'v0.1.0', 'versionName': '0.1.0', 'versionCode': 9, 'gitCommit': 'a' * 40}
            info = {'apkSha256': hashlib.sha256(source.read_bytes()).hexdigest()}
            with patch.object(release, 'validate_tag', return_value=validation), patch.object(release, 'verify_apk', return_value=info):
                result = release.package_release('v0.1.0', root)
                self.assertEqual([f'NetProbe-0.1.0-{kind}.apk' for kind in release.DISTRIBUTIONS], [apk['apkFile'] for apk in result['apks']])
                self.assertEqual(4, len((root / 'dist/SHA256SUMS').read_text().splitlines()))
                self.assertEqual('public-shared-project-key', result['signingPolicy'])
                metadata = json.loads((root / 'dist/build-info.json').read_text())
                self.assertEqual('a' * 40, metadata['gitCommit'])
                for line in (root / 'dist/SHA256SUMS').read_text().splitlines():
                    digest, name = line.split('  ', 1)
                    self.assertEqual(digest, hashlib.sha256((root / 'dist' / name).read_bytes()).hexdigest())
                with self.assertRaises(ValueError):
                    release.package_release('v0.1.0', root)

    def test_apk_version_and_signer_mismatches_fail(self):
        with tempfile.TemporaryDirectory(prefix='netprobe-verify-test-') as directory:
            root = Path(directory)
            (root / 'app').mkdir()
            (root / 'app/build.gradle.kts').write_text(config('0.1.0', 9))
            (root / 'signing').mkdir()
            (root / 'signing/certificate-sha256.txt').write_text('a' * 64)
            apk = root / 'app.apk'
            apk.write_bytes(b'test')
            signature = 'Signer #1 certificate SHA-256 digest: ' + 'b' * 64
            metadata = "package: name='com.rnotlnglgq.netprobe' versionCode='9' versionName='0.1.0'"
            with patch.object(release, 'android_sdk', return_value=root), patch.object(release, 'run', side_effect=[signature, metadata]):
                with self.assertRaisesRegex(ValueError, 'certificate'):
                    release.verify_apk(apk, root=root)
            with patch.object(release, 'android_sdk', return_value=root), patch.object(release, 'run', side_effect=[signature, metadata.replace("versionCode='9'", "versionCode='8'")]):
                with self.assertRaisesRegex(ValueError, 'metadata'):
                    release.verify_apk(apk, root=root)


if __name__ == '__main__':
    unittest.main()
