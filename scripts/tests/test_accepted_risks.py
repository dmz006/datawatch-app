"""Light, offline tests for the accepted-risks registry lint and watch helpers.

Run: python3 -m unittest discover -s scripts/tests
"""
import contextlib
import copy
import io
import datetime
import os
import sys
import unittest

SCRIPTS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(SCRIPTS)
sys.path.insert(0, SCRIPTS)
import accepted_risks  # noqa: E402
import check_accepted_risks  # noqa: E402
import sca_fix_watch  # noqa: E402

TODAY = datetime.date(2026, 10, 8)
REGISTRY = os.path.join(ROOT, "security", "accepted-risks.yml")

GOOD = {
    "id": "dependabot/99",
    "kind": "dependency",
    "package": "io.ktor:ktor-client-core",
    "severity": "medium",
    "fixed_in": "3.0.1",
    "impact": {"traced": True, "reachable": "no", "analysis": "Server-side only API; never called."},
    "added": "2026-10-01",
    "expires": "2026-12-30",
    "validated_by": "claude-session",
    "reason": "Waiting for a stable release.",
}


def errs(entry, today=TODAY):
    return accepted_risks.validate([entry], today)[0]


def variant(**changes):
    e = copy.deepcopy(GOOD)
    for k, v in changes.items():
        if v is None:
            e.pop(k, None)
        else:
            e[k] = v
    return e


class ParserTest(unittest.TestCase):
    def test_nested_and_folded(self):
        doc = accepted_risks.loads(
            "# header\nrisks:\n  - id: code-scanning/1\n    impact:\n      traced: false\n"
            "      reachable: \"unknown\"\n      analysis: >-\n        line one\n        #2 is text\n"
            "    added: 2026-10-01\n  - id: osv/GHSA-x\n"
        )
        r = doc["risks"]
        self.assertEqual(len(r), 2)
        self.assertIs(r[0]["impact"]["traced"], False)
        self.assertEqual(r[0]["impact"]["reachable"], "unknown")
        self.assertEqual(r[0]["impact"]["analysis"], "line one #2 is text")
        self.assertEqual(r[0]["added"], "2026-10-01")
        self.assertEqual(r[1]["id"], "osv/GHSA-x")

    def test_empty_list(self):
        self.assertEqual(accepted_risks.loads("risks: []\n")["risks"], [])

    def test_matches_pyyaml_on_registry(self):
        try:
            import yaml
        except ImportError:
            self.skipTest("PyYAML not installed")

        def norm(x):
            if isinstance(x, dict):
                return {k: norm(v) for k, v in x.items()}
            return x.isoformat() if isinstance(x, datetime.date) else x

        with open(REGISTRY, encoding="utf-8") as f:
            expected = [norm(e) for e in yaml.safe_load(f)["risks"]]
        self.assertEqual(accepted_risks.load(REGISTRY), expected)


class ValidateTest(unittest.TestCase):
    def test_good_entry(self):
        self.assertEqual(errs(GOOD), [])

    def test_repo_registry_passes(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(check_accepted_risks.main([REGISTRY, "--today", "2026-10-08"]), 0)

    def test_missing_field(self):
        for f in accepted_risks.REQUIRED:
            with self.subTest(field=f):
                self.assertTrue(any("`%s`" % f in e for e in errs(variant(**{f: None}))))

    def test_bad_values(self):
        self.assertTrue(errs(variant(id="ghsa/1")))
        self.assertTrue(errs(variant(kind="npm")))
        self.assertTrue(errs(variant(severity="urgent")))
        self.assertTrue(errs(variant(package="ktor-client-core")))  # dependency needs group:artifact
        self.assertTrue(errs(variant(validated_by="not a login")))
        self.assertTrue(errs(variant(added="2026-13-01")))
        self.assertTrue(errs(variant(expires="soon")))

    def test_duplicate_id(self):
        self.assertTrue(any("duplicate" in e for e in accepted_risks.validate([GOOD, GOOD], TODAY)[0]))

    def test_expiry_limits(self):
        self.assertEqual(errs(variant(expires="2026-12-30")), [])  # added + 90
        self.assertTrue(errs(variant(expires="2026-12-31")))  # added + 91
        untraced = {"traced": False, "reachable": "unknown", "analysis": "Not traced yet."}
        self.assertEqual(errs(variant(impact=untraced, expires="2026-10-31")), [])  # added + 30
        self.assertTrue(errs(variant(impact=untraced, expires="2026-11-01")))  # added + 31

    def test_untraced_needs_reachable_and_analysis(self):
        self.assertTrue(errs(variant(impact={"traced": False, "reachable": "unknown"}, expires="2026-10-20")))
        self.assertTrue(errs(variant(impact={"traced": False, "analysis": "x"}, expires="2026-10-20")))
        self.assertTrue(errs(variant(impact={"reachable": "no", "analysis": "x"})))  # traced must be bool

    def test_expired(self):
        self.assertEqual(errs(GOOD, today=datetime.date(2026, 12, 30)), [])
        self.assertTrue(any("expired" in e for e in errs(GOOD, today=datetime.date(2026, 12, 31))))

    def test_escalation_warning(self):
        e = variant(severity="high", impact={"traced": True, "reachable": "yes", "analysis": "x"})
        errors, warnings = accepted_risks.validate([e], TODAY)
        self.assertEqual(errors, [])
        self.assertEqual(len(warnings), 1)


class WatchHelpersTest(unittest.TestCase):
    def test_ge(self):
        self.assertTrue(sca_fix_watch.ge("2.4.20", "2.4.20-Beta1"))
        self.assertTrue(sca_fix_watch.ge("2.5.0", "2.4.20"))
        self.assertFalse(sca_fix_watch.ge("2.4.10", "2.4.20"))

    def test_rereview(self):
        due = sca_fix_watch.rereview([GOOD], datetime.date(2026, 12, 16))
        self.assertEqual([d[2] for d in due], [14])
        self.assertEqual(sca_fix_watch.rereview([GOOD], datetime.date(2026, 12, 15)), [])
        self.assertEqual(sca_fix_watch.rereview([GOOD], datetime.date(2027, 1, 2))[0][2], -3)

    def test_unregistered(self):
        alerts = [{"number": 14}, {"number": 16}]
        got = sca_fix_watch.unregistered_dismissals(alerts, "code-scanning", {"code-scanning/14"})
        self.assertEqual([rid for rid, _ in got], ["code-scanning/16"])

    def test_osv_acceptance_by_id_or_alias(self):
        risks = [{"id": "osv/GHSA-aaaa", "kind": "bundled-js"}, {"id": "cve/CVE-2026-1", "kind": "bundled-js"},
                 {"id": "osv/GHSA-bbbb", "kind": "dependency"}]
        accepted = sca_fix_watch.accepted_vuln_ids(risks)
        self.assertTrue(sca_fix_watch.is_accepted({"id": "GHSA-aaaa"}, accepted))
        self.assertTrue(sca_fix_watch.is_accepted({"id": "GHSA-zzzz", "aliases": ["CVE-2026-1"]}, accepted))
        self.assertFalse(sca_fix_watch.is_accepted({"id": "GHSA-bbbb"}, accepted))  # wrong kind

    def test_read_bundled(self):
        names = {n for n, _, _ in sca_fix_watch.read_bundled()}
        self.assertTrue({"@xterm/xterm", "@xterm/addon-fit", "@xterm/addon-search", "mermaid"} <= names)

    def test_offline_main(self):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            self.assertEqual(sca_fix_watch.main([REGISTRY, "--offline", "--today", "2026-10-08"]), 0)
        self.assertIn("## Re-review due", buf.getvalue())


if __name__ == "__main__":
    unittest.main()
