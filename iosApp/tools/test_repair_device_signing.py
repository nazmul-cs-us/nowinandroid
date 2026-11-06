"""Exercise local config repair with command fixtures; actual signing needs Xcode."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class DeviceSigningRepairTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'tools').mkdir()
        (self.root / 'Config').mkdir()
        (self.root / 'iosApp.xcodeproj').mkdir()
        self.script = self.root / 'tools/repair-device-signing.sh'
        shutil.copyfile(Path(__file__).with_name(self.script.name), self.script)
        self.local = self.root / 'Config/Local.xcconfig'
        self.local.write_text('PRAYER_PUSH_BACKEND_URL = preserved-value\n')
        binaries = self.root / 'bin'
        binaries.mkdir()
        self.environment = dict(os.environ, PATH=str(binaries) + os.pathsep + os.environ['PATH'])
        self.command(binaries, 'xcodebuild', """
import json, os, sys
if '-alltargets' not in sys.argv and os.environ.get('DEENLY_TEST_BUILD_SETTINGS_FAIL'):
    print('private-build-setting-marker', file=sys.stderr)
    sys.exit(1)
if '-alltargets' not in sys.argv:
    rows = [
        {'target': 'PrayerWidgetExtension', 'buildSettings': {'DEVELOPMENT_TEAM': 'OTHERTEAM1'}},
        {'target': 'iosApp', 'buildSettings': {'DEVELOPMENT_TEAM': 'A1B2C3D4E5'}}
    ]
else:
    rows = [{'target': name, 'buildSettings': {
        'DEVELOPMENT_TEAM': os.environ.get('DEENLY_TEST_EXPECTED_TEAM', 'A1B2C3D4E5'),
        'CODE_SIGN_IDENTITY': 'Apple Development', 'CODE_SIGN_STYLE': 'Automatic',
        'CODE_SIGNING_ALLOWED': 'YES', 'CODE_SIGNING_REQUIRED': 'YES'
    }} for name in ['iosApp', 'PrayerLiveActivityExtension', 'PrayerWidgetExtension']]
    if os.environ.get('DEENLY_TEST_ADHOC'):
        rows[-1]['buildSettings']['CODE_SIGN_IDENTITY'] = '-'
    if os.environ.get('DEENLY_TEST_WRONG_TEAM'):
        rows[-1]['buildSettings']['DEVELOPMENT_TEAM'] = 'OTHERTEAM1'
    if os.environ.get('DEENLY_TEST_MISSING_EXTENSION'):
        rows.pop()
print(json.dumps(rows))
""")
        self.command(binaries, 'xcodegen', """
import json, os, pathlib, sys
pathlib.Path('generation.json').write_text(json.dumps(sys.argv[1:]))
sys.exit(1 if os.environ.get('DEENLY_TEST_GENERATION_FAIL') else 0)
""")

    def command(self, directory, name, body):
        path = directory / name
        path.write_text('#!' + sys.executable + '\n' + body)
        path.chmod(0o755)

    def run_repair(self, *arguments):
        return subprocess.run(['bash', str(self.script), *arguments], env=self.environment,
                              capture_output=True, text=True)

    def test_reuses_parent_team_and_overrides_adhoc_debug_settings(self):
        self.local.write_text('CODE_SIGN_IDENTITY = -\nCODE_SIGNING_ALLOWED = NO\n')
        result = self.run_repair()
        self.assertEqual(0, result.returncode, result.stderr)
        text = self.local.read_text()
        self.assertIn('DEVELOPMENT_TEAM[config=Debug][sdk=iphoneos*] = A1B2C3D4E5', text)
        self.assertNotIn('OTHERTEAM1', text)
        self.assertIn('resolved Debug signing settings verified', result.stdout)
        self.assertIn('CODE_SIGN_IDENTITY[config=Debug][sdk=iphoneos*] = Apple Development', text)
        self.assertIn('CODE_SIGNING_ALLOWED[config=Debug][sdk=iphoneos*] = YES', text)
        self.assertEqual(['generate', '--spec', 'project.yml'],
                         json.loads((self.root / 'generation.json').read_text()))

    def test_preserves_local_settings_and_is_idempotent(self):
        original = ('PRAYER_PUSH_BACKEND_URL = preserved-value\n'
                    'PROVISIONING_PROFILE_SPECIFIER[config=Release] = Production profile\n')
        self.local.write_text(original)
        self.assertEqual(0, self.run_repair().returncode)
        first = self.local.read_text()
        self.assertIn(original, first)
        self.assertEqual(0, self.run_repair().returncode)
        self.assertEqual(first, self.local.read_text())

    def test_explicit_team_works_without_an_existing_project(self):
        (self.root / 'iosApp.xcodeproj').rmdir()
        self.environment['DEENLY_TEST_BUILD_SETTINGS_FAIL'] = '1'
        self.environment['DEENLY_TEST_EXPECTED_TEAM'] = 'Z9Y8X7W6V5'
        result = self.run_repair('Z9Y8X7W6V5')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('= Z9Y8X7W6V5', self.local.read_text())

    def test_invalid_team_changes_nothing(self):
        before = self.local.read_bytes()
        result = self.run_repair('not-a-team')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, self.local.read_bytes())
        self.assertFalse((self.root / 'generation.json').exists())

    def test_build_settings_failure_changes_nothing_and_withholds_raw_output(self):
        before = self.local.read_bytes()
        self.environment['DEENLY_TEST_BUILD_SETTINGS_FAIL'] = '1'
        result = self.run_repair()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, self.local.read_bytes())
        self.assertNotIn('private-build-setting-marker', result.stdout + result.stderr)
        self.assertFalse((self.root / 'generation.json').exists())

    def test_incomplete_local_block_is_preserved_for_manual_repair(self):
        self.local.write_text('// BEGIN DEENLY DEBUG DEVICE SIGNING\n')
        before = self.local.read_bytes()
        self.assertNotEqual(0, self.run_repair().returncode)
        self.assertEqual(before, self.local.read_bytes())

    def test_generation_failure_does_not_claim_success(self):
        self.environment['DEENLY_TEST_GENERATION_FAIL'] = '1'
        result = self.run_repair()
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('Project regenerated.', result.stdout)

    def test_adhoc_extension_fails_resolved_settings_check(self):
        self.environment['DEENLY_TEST_ADHOC'] = '1'
        result = self.run_repair()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('FAIL: PrayerWidgetExtension: identity=ad hoc', result.stdout)
        self.assertNotIn('signing settings verified', result.stdout)

    def test_extension_team_mismatch_fails_resolved_settings_check(self):
        self.environment['DEENLY_TEST_WRONG_TEAM'] = '1'
        result = self.run_repair()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('team matches=False', result.stdout)
        self.assertNotIn('signing settings verified', result.stdout)

    def test_missing_extension_is_not_treated_as_success(self):
        self.environment['DEENLY_TEST_MISSING_EXTENSION'] = '1'
        result = self.run_repair()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('FAIL: PrayerWidgetExtension: missing', result.stdout)


if __name__ == '__main__':
    unittest.main()
