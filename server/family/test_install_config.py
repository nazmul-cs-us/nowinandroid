"""Check the installer's pure config migration without touching service files."""
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
                        if isinstance(node, ast.FunctionDef) and node.name == 'ensure_signup_option')
        scope = {'re': re, 'tomllib': tomllib}
        exec(compile(ast.Module(body=[function], type_ignores=[]), 'installer-pure-migration', 'exec'), scope)
        cls.migrate = staticmethod(scope['ensure_signup_option'])

    def test_adds_setting_for_a_new_family_module(self):
        result = self.migrate('[modules.other]\nexample = 7\n')
        modules = tomllib.loads(result)['modules']
        self.assertIs(True, modules['mod_deenly_family']['signup_enabled'])
        self.assertEqual(7, modules['other']['example'])

    def test_upgrades_an_empty_existing_module_and_is_idempotent(self):
        initial = '[modules.mod_deenly_family]\n\n[modules.other]\nexample = 7\n'
        result = self.migrate(initial)
        self.assertIs(True, tomllib.loads(result)['modules']['mod_deenly_family']['signup_enabled'])
        self.assertEqual(result, self.migrate(result))

    def test_preserves_explicit_administrator_disable(self):
        initial = '[modules.mod_deenly_family]\nsignup_enabled = false\n'
        self.assertEqual(initial, self.migrate(initial))
        self.assertIs(False, tomllib.loads(self.migrate(initial))['modules']['mod_deenly_family']['signup_enabled'])

    def test_preserves_header_comments_and_other_settings(self):
        initial = '[modules.mod_deenly_family] # existing setup\n\n[general]\nlanguage = "en"\n'
        result = self.migrate(initial)
        self.assertIn('# existing setup', result)
        self.assertEqual('en', tomllib.loads(result)['general']['language'])


if __name__ == '__main__':
    unittest.main()
