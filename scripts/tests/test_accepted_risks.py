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
    "version": "2.3.13",
    "catalog": "ktor",
    "severity": "medium",
    "fixed_in": "3.0.1",
    "impact": {
        "traced": True,
        "reachable": "no",
        "method": "rg for the affected API in composeApp/ and shared/; no call sites",
        "analysis": "Server-side only API; never called.",
    },
    "first_added": "2026-10-01",
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
        e = variant(severity="high", impact={"traced": True, "reachable": "yes", "method": "m", "analysis": "x"})
        errors, warnings = accepted_risks.validate([e], TODAY)
        self.assertEqual(errors, [])
        self.assertEqual(len(warnings), 1)


class SharedSchemaTest(unittest.TestCase):
    """Fields agreed with datawatch (#197): version/path, method, first_added."""

    CS = {
        "id": "code-scanning/77",
        "kind": "code-scanning",
        "package": "java/android/some-rule",
        "path": "composeApp/src/androidMain/kotlin/X.kt",
        "severity": "medium",
        "impact": {"traced": True, "reachable": "no", "method": "code-path review of X.kt", "analysis": "x"},
        "first_added": "2026-10-01",
        "added": "2026-10-01",
        "expires": "2026-12-30",
        "validated_by": "claude-session",
        "reason": "r",
    }

    def test_code_scanning_entry(self):
        self.assertEqual(errs(self.CS), [])
        e = copy.deepcopy(self.CS)
        del e["path"]
        self.assertTrue(any("`path`" in m for m in errs(e)))  # path required, version optional

    def test_version_required_for_versioned_kinds(self):
        self.assertTrue(any("`version`" in m for m in errs(variant(version=None))))
        js = variant(id="osv/GHSA-1", kind="bundled-js", package="mermaid", version=None)
        self.assertTrue(any("`version`" in m for m in errs(js)))
        self.assertEqual(errs(variant(id="osv/GHSA-1", kind="bundled-js", package="mermaid", version="12.1.0")), [])

    def test_method_required_when_traced(self):
        imp = {"traced": True, "reachable": "no", "analysis": "x"}
        self.assertTrue(any("impact.method" in m for m in errs(variant(impact=imp))))
        untraced = {"traced": False, "reachable": "unknown", "analysis": "x"}
        self.assertEqual(errs(variant(impact=untraced, expires="2026-10-31")), [])

    def test_first_added_not_after_added(self):
        self.assertTrue(any("first_added" in m for m in errs(variant(first_added="2026-10-02"))))
        self.assertEqual(errs(variant(first_added="2026-07-01")), [])
        self.assertTrue(errs(variant(first_added="someday")))

    def test_expiry_relative_to_added_not_first_added(self):
        renewed = variant(first_added="2026-06-01", added="2026-10-01", expires="2026-12-30")
        self.assertEqual(accepted_risks.validate([renewed], TODAY)[0], [])

    def test_images_list(self):
        self.assertEqual(errs(variant(images=["a", "b"])), [])
        self.assertTrue(errs(variant(images="agent-base")))
        doc = accepted_risks.loads("risks:\n  - id: x\n    images: [agent-base, parent-full]\n")
        self.assertEqual(doc["risks"][0]["images"], ["agent-base", "parent-full"])

    def test_renewal_past_first_expiry_escalates(self):
        # first window: 2026-07-01 + 90 d = 2026-09-29; renewed 2026-09-15
        renewed = variant(first_added="2026-07-01", added="2026-09-15", expires="2026-12-14")
        errors, esc = accepted_risks.validate([renewed], TODAY)
        self.assertEqual(errors, [])
        self.assertEqual(len(esc), 1)
        self.assertIn("first expiry", esc[0])
        # still inside the first window -> no escalation
        self.assertEqual(accepted_risks.validate([renewed], datetime.date(2026, 9, 29))[1], [])
        # never renewed (added == first_added) -> no escalation
        self.assertEqual(accepted_risks.validate([GOOD], datetime.date(2026, 12, 30))[1], [])
        # untraced window is 30 days
        untraced = variant(impact={"traced": False, "reachable": "unknown", "analysis": "x"},
                           first_added="2026-09-01", added="2026-09-20", expires="2026-10-20")
        self.assertEqual(len(accepted_risks.validate([untraced], TODAY)[1]), 1)  # 09-01 + 30 = 10-01

    def test_cli_prints_escalate_and_passes(self):
        import tempfile
        e = variant(severity="critical", impact={"traced": True, "reachable": "yes", "method": "m", "analysis": "x"})
        text = (
            "risks:\n  - id: %(id)s\n    kind: %(kind)s\n    package: %(package)s\n    version: \"%(version)s\"\n"
            "    severity: %(severity)s\n    impact:\n      traced: true\n      reachable: \"yes\"\n      method: m\n"
            "      analysis: x\n    first_added: %(first_added)s\n    added: %(added)s\n    expires: %(expires)s\n"
            "    validated_by: %(validated_by)s\n    reason: r\n" % e
        )
        with tempfile.NamedTemporaryFile("w", suffix=".yml", delete=False) as f:
            f.write(text)
        try:
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                self.assertEqual(check_accepted_risks.main([f.name, "--today", "2026-10-08"]), 0)
            self.assertIn("ESCALATE: dependabot/99: reachable=yes on a critical finding", buf.getvalue())
        finally:
            os.unlink(f.name)

    def test_first_added_immutable(self):
        old = [variant(), dict(self.CS)]
        self.assertEqual(accepted_risks.first_added_changes(old, old), [])
        changed = [variant(first_added="2026-09-01"), dict(self.CS)]
        self.assertEqual(len(accepted_risks.first_added_changes(old, changed)), 1)
        self.assertEqual(accepted_risks.first_added_changes(old, [variant(id="dependabot/5")]), [])  # new entry


class ChangeInvalidationTest(unittest.TestCase):
    TOML = (
        '[versions]\nktor = "2.3.13"\nokio = "3.9.0" # comment\n\n[libraries]\n'
        'ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }\n'
        'okio = { group = "com.squareup.okio", name = "okio", version.ref = "okio" }\n'
        'pinned = { module = "a.b:c", version = "1.0" }\n\n[plugins]\nx = { id = "x", version.ref = "ktor" }\n'
    )

    def catalog(self):
        import tempfile
        with tempfile.NamedTemporaryFile("w", suffix=".toml", delete=False) as f:
            f.write(self.TOML)
        try:
            return sca_fix_watch.read_catalog(f.name)
        finally:
            os.unlink(f.name)

    def test_read_catalog(self):
        versions, modules = self.catalog()
        self.assertEqual(versions["okio"], "3.9.0")
        self.assertEqual(modules, {"io.ktor:ktor-client-core": "2.3.13", "com.squareup.okio:okio": "3.9.0", "a.b:c": "1.0"})

    def test_repo_catalog_has_ktor(self):
        versions, modules = sca_fix_watch.read_catalog()
        self.assertIn("ktor", versions)
        self.assertEqual(modules.get("io.ktor:ktor-client-core"), versions["ktor"])

    def test_dependency_version_change(self):
        cat = self.catalog()
        self.assertEqual(sca_fix_watch.retrace([GOOD], cat, [])[0], [])
        rows, _ = sca_fix_watch.retrace([variant(version="2.3.12")], cat, [])
        self.assertEqual([(r[1], r[2]) for r in rows], [("2.3.12", "2.3.13")])
        # no catalog key -> resolved through [libraries]
        rows, _ = sca_fix_watch.retrace([variant(catalog=None, package="com.squareup.okio:okio", version="3.8.0")], cat, [])
        self.assertEqual(rows[0][2], "3.9.0")
        rows, notes = sca_fix_watch.retrace([variant(catalog=None, package="x.y:z")], cat, [])
        self.assertEqual(rows, [])
        self.assertTrue(notes)

    def test_bundled_js_version_change(self):
        bundled = [("mermaid", "12.1.0", "mermaid.VERSION")]
        js = variant(id="osv/GHSA-1", kind="bundled-js", package="mermaid", version="12.1.0")
        self.assertEqual(sca_fix_watch.retrace([js], ({}, {}), bundled)[0], [])
        js["version"] = "11.9.0"
        self.assertEqual(len(sca_fix_watch.retrace([js], ({}, {}), bundled)[0]), 1)

    def test_code_scanning_path_change(self):
        calls = []

        def fake_git(path, added, version):
            calls.append((path, added, version))
            return (["abc1234 2026-10-07 touch X"], None) if path.endswith("X.kt") else ([], None)

        cs = dict(SharedSchemaTest.CS)
        rows, _ = sca_fix_watch.retrace([cs], ({}, {}), [], git=fake_git)
        self.assertEqual(len(rows), 1)
        self.assertEqual(calls[0][1], datetime.date(2026, 10, 1))
        cs2 = dict(cs, path=None, version="Y.kt@deadbee")
        self.assertEqual(sca_fix_watch.retrace([cs2], ({}, {}), [], git=fake_git)[0], [])
        self.assertEqual(calls[-1][0], "Y.kt")
        section, n = sca_fix_watch.retrace_section([cs], [], ({}, {}), [], git=fake_git)
        self.assertEqual(n, 1)
        self.assertIn("## Re-trace needed (dependency/code changed)", section)

    def test_git_changes_real_repo(self):
        # BiometricGate.kt was last changed 2026-10-05, before the entries' added date.
        path = "composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/security/BiometricGate.kt"
        commits, err = sca_fix_watch.git_changes(path, datetime.date(2026, 10, 6))
        if err:
            self.skipTest("git unavailable: %s" % err)
        if sca_fix_watch.is_shallow():
            self.skipTest("shallow clone")
        self.assertEqual(commits, [])
        commits, _ = sca_fix_watch.git_changes(path, datetime.date(2026, 10, 5))
        self.assertTrue(commits)


class RecentTest(unittest.TestCase):
    def test_recent_entries(self):
        today = datetime.date(2026, 10, 2)
        old = variant(id="dependabot/1", added="2026-09-30", first_added="2026-09-30", expires="2026-10-20")
        got = sca_fix_watch.recent_entries([GOOD, old], today)  # GOOD added 2026-10-01 = yesterday
        self.assertEqual([r["id"] for r in got], ["dependabot/99"])
        self.assertEqual(sca_fix_watch.recent_entries([GOOD], datetime.date(2026, 10, 3)), [])

    def test_recent_section(self):
        lines, n = sca_fix_watch.recent_section([GOOD], datetime.date(2026, 10, 1), [], commits_fn=lambda: ([], None))
        text = "\n".join(lines)
        self.assertEqual(n, 1)
        self.assertIn("dependabot/99 (new)", text)
        renewed = variant(first_added="2026-07-01")
        self.assertIn("(renewal)", "\n".join(sca_fix_watch.recent_section(
            [renewed], datetime.date(2026, 10, 1), [], commits_fn=lambda: ([], None))[0]))
        lines, n = sca_fix_watch.recent_section([], TODAY, [], commits_fn=lambda: (["abc a: msg"], None))
        self.assertEqual(n, 1)
        self.assertIn("- `abc a: msg`", lines)
        lines, n = sca_fix_watch.recent_section([], TODAY, [], commits_fn=lambda: ([], None))
        self.assertEqual((n, lines[-1]), (0, "Nothing added or renewed."))


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
