#!/usr/bin/env python3
"""Set the Google Group(s) that may test a Play closed track.

Env: PLAY_KEY_JSON (service-account JSON). Usage:
  play_testers.py --package com.dmzs.datawatchclient --track alpha --group datawatch-testers@googlegroups.com
Commits the edit; existing groups on the track are kept unless --replace.
"""
import argparse
import json
import os
import sys

import google.auth.transport.requests as gtr
from google.oauth2 import service_account


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", required=True)
    ap.add_argument("--track", required=True)
    ap.add_argument("--group", action="append", required=True)
    ap.add_argument("--replace", action="store_true")
    a = ap.parse_args()
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_KEY_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    s = gtr.AuthorizedSession(creds)
    base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{a.package}/edits"
    edit = s.post(base).json()["id"]
    committed = False
    try:
        cur = s.get(f"{base}/{edit}/testers/{a.track}")
        groups = [] if a.replace or not cur.ok else cur.json().get("googleGroups", [])
        for g in a.group:
            if g not in groups:
                groups.append(g)
        r = s.put(f"{base}/{edit}/testers/{a.track}", json={"googleGroups": groups})
        if not r.ok:
            print(f"::error::set testers failed: {r.status_code} {r.text[:400]}")
            return 1
        c = s.post(f"{base}/{edit}:commit")
        if not c.ok:
            print(f"::error::commit failed: {c.status_code} {c.text[:400]}")
            return 1
        committed = True
        print(f"{a.track} tester groups: " + ", ".join(groups))
        return 0
    finally:
        if not committed:
            s.delete(f"{base}/{edit}")


if __name__ == "__main__":
    sys.exit(main())
