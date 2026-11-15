import tempfile
import unittest
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa

from apns_check import check_apns_config, read_environment


class ApnsReadinessTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def settings(self, key):
        path = self.root / "synthetic.p8"
        path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        return {"FAMILY_APNS_TEAM_ID": "ABCDE12345", "FAMILY_APNS_KEY_ID": "12345ABCDE",
                "FAMILY_APNS_BUNDLE_ID": "com.starception.submission", "FAMILY_APNS_KEY_FILE": str(path)}

    def test_valid_real_p256_key(self):
        self.assertEqual([], check_apns_config(self.settings(ec.generate_private_key(ec.SECP256R1()))))

    def test_missing_apple_configuration_explains_next_steps(self):
        issues = check_apns_config({})
        self.assertEqual(3, len(issues))
        self.assertTrue(any("FAMILY_APNS_KEY_FILE" in issue for issue in issues))

    def test_rejects_wrong_key_type_and_curve(self):
        for key in [rsa.generate_private_key(public_exponent=65537, key_size=2048),
                    ec.generate_private_key(ec.SECP384R1())]:
            with self.subTest(type=type(key)):
                self.assertTrue(any("P-256" in issue for issue in check_apns_config(self.settings(key))))

    def test_bad_key_diagnostics_do_not_include_contents(self):
        path = self.root / "bad.p8"
        path.write_text("synthetic-private-content-never-print")
        issues = check_apns_config({"FAMILY_APNS_KEY_FILE": str(path)})
        self.assertNotIn(path.read_text(), " ".join(issues))

    def test_environment_quotes_and_comments_do_not_execute_shell(self):
        path = self.root / "push.env"
        path.write_text('# Comment\nFAMILY_APNS_KEY_FILE="/etc/keys/a key.p8"\n'
                        'EMPTY=\nVALUE=ABC # comment\nLITERAL="$(touch impossible)"\n')
        settings = read_environment(path)
        self.assertEqual("/etc/keys/a key.p8", settings["FAMILY_APNS_KEY_FILE"])
        self.assertEqual("", settings["EMPTY"])
        self.assertEqual("ABC", settings["VALUE"])
        self.assertEqual("$(touch impossible)", settings["LITERAL"])


if __name__ == "__main__":
    unittest.main()
