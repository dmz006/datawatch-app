#!/usr/bin/env python3
"""Promote the newest release on a Play track to another track (no re-upload).

Copies the version codes and release notes of the latest release on
--from-track to --to-track as a completed release, then commits the edit.
Google reviews closed/open/production updates before testers get them.

Env: PLAY_KEY_JSON (service-account JSON). Usage:
  play_promote.py --package com.dmzs.datawatchclient --from-track internal --to-track alpha [--version-code N]
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
    ap.add_argument("--from-track", required=True)
    ap.add_argument("--to-track", required=True)
    ap.add_argument("--version-code", help="promote this code instead of the newest on --from-track")
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
        src = s.get(f"{base}/{edit}/tracks/{a.from_track}").json()
        releases = [r for r in src.get("releases", []) if r.get("versionCodes")]
        if a.version_code:
            releases = [r for r in releases if a.version_code in r["versionCodes"]]
        if not releases:
            print(f"::error::no release on {a.from_track}" + (f" with code {a.version_code}" if a.version_code else ""))
            return 1
        rel = max(releases, key=lambda r: max(int(c) for c in r["versionCodes"]))
        new = {k: rel[k] for k in ("name", "versionCodes", "releaseNotes") if k in rel}
        new["status"] = "completed"
        r = s.put(f"{base}/{edit}/tracks/{a.to_track}", json={"track": a.to_track, "releases": [new]})
        if not r.ok:
            print(f"::error::update {a.to_track} failed: {r.status_code} {r.text[:400]}")
            return 1
        c = s.post(f"{base}/{edit}:commit")
        if not c.ok:
            print(f"::error::commit failed: {c.status_code} {c.text[:400]}")
            return 1
        committed = True
        print(f"Promoted {new.get('name', '')} ({','.join(new['versionCodes'])}) {a.from_track} -> {a.to_track}")
        return 0
    finally:
        if not committed:
            s.delete(f"{base}/{edit}")


if __name__ == "__main__":
    sys.exit(main())
