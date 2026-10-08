#!/usr/bin/env python3
"""CI lint for security/accepted-risks.yml (AGENT.md § Security acceptance standard).

Fails (exit 1) when an entry is missing a required field, has an invalid id /
kind / severity / date, expires more than 90 days (traced) or 30 days (not
traced) after `added`, lacks impact.reachable + impact.analysis, or has already
expired. Warns (exit 0) when an entry needs operator escalation
(reachable=yes on a HIGH/CRITICAL finding).

usage: check_accepted_risks.py [registry] [--today YYYY-MM-DD]
"""
import argparse
import datetime
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import accepted_risks  # noqa: E402


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("registry", nargs="?", default="security/accepted-risks.yml")
    ap.add_argument("--today", type=datetime.date.fromisoformat, default=None)
    a = ap.parse_args(argv)
    try:
        risks = accepted_risks.load(a.registry)
    except (OSError, ValueError) as e:
        print("::error file=%s::cannot read registry: %s" % (a.registry, e))
        return 1
    errors, warnings = accepted_risks.validate(risks, a.today)
    for w in warnings:
        print("::warning file=%s::%s" % (a.registry, w))
    for e in errors:
        print("::error file=%s::%s" % (a.registry, e))
    print("%s: %d entr%s, %d error(s), %d warning(s)" % (
        a.registry, len(risks), "y" if len(risks) == 1 else "ies", len(errors), len(warnings)))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
