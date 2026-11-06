"""Check the installer's pure config repair without touching service files."""
import ast
from pathlib import Path
import re
import tomllib
import unittest


class SignupConfigMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        shell = Path(__file__).with_name('install.sh').read_text()
        python = shell.split("<<'PY'\n", 1)[1].split('\nPY\n', 1)[0]
        function = next(node for node in ast.parse(python).body
                        if isinstance(node, ast.FunctionDef) and node.name == 'ensure_family_module')
        scope = {'re': re, 'tomllib': tomllib}
        exec(compile(ast.Module(body=[function], type_ignores=[]), 'installer-pure-migration', 'exec'), scope)
        cls.migrate = staticmethod(scope['ensure_family_module'])

    def test_adds_an_empty_custom_module_section(self):
        result = self.migrate('[modules.other]\nexample = 7\n')
        modules = tomllib.loads(result)['modules']
        self.assertEqual({}, modules['mod_deenly_family'])
        self.assertEqual(7, modules['other']['example'])

    def test_existing_empty_module_is_unchanged(self):
        initial = '[modules.mod_deenly_family]\n\n[modules.other]\nexample = 7\n'
        self.assertEqual(initial, self.migrate(initial))

    def test_repairs_incompatible_signup_key_and_is_idempotent(self):
        initial = '[modules.mod_deenly_family] # existing setup\nsignup_enabled = true\n\n[general]\nlanguage = "en"\n'
        result = self.migrate(initial)
        self.assertEqual({}, tomllib.loads(result)['modules']['mod_deenly_family'])
        self.assertIn('# existing setup', result)
        self.assertEqual('en', tomllib.loads(result)['general']['language'])
        self.assertEqual(result, self.migrate(result))

    def test_does_not_silently_enable_a_disabled_signup_policy(self):
        with self.assertRaisesRegex(SystemExit, 'FAMILY_SIGNUP_ENABLED'):
            self.migrate('[modules.mod_deenly_family]\nsignup_enabled = false\n')

    def test_does_not_remove_unrelated_module_options(self):
        with self.assertRaises(SystemExit):
            self.migrate('[modules.mod_deenly_family]\ncustom_option = "preserve me"\n')

    def test_repair_is_limited_to_the_family_section(self):
        initial = '[modules.mod_deenly_family]\nsignup_enabled = true\n[modules.other]\nsignup_enabled = true\n'
        result = tomllib.loads(self.migrate(initial))['modules']
        self.assertEqual({}, result['mod_deenly_family'])
        self.assertTrue(result['other']['signup_enabled'])


if __name__ == '__main__':
    unittest.main()
