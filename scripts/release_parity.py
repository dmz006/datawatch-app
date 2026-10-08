#!/usr/bin/env python3
"""Release parity: every channel carries the release's version (AGENT.md § Release parity).

  release_parity.py wait-ios  --code N [--timeout-min 60]
      Wait until App Store Connect has processed build N (VALID). Exit 1 on
      timeout or a failed/invalid build.
  release_parity.py verify    --code N
      Read-only check, exit 1 if any channel is behind:
        Play internal + alpha (phone) carry N
        Play wear:internal + "wear:Wear closed testing" carry 100000 + N
        TestFlight build N is submitted to / passed Beta App Review

Env: PLAY_KEY_JSON (Play service-account JSON) for verify;
     ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_BASE64 for both.
"""
import argparse
import base64
import json
import os
import sys
import time

import jwt
import requests

PACKAGE = "com.dmzs.datawatchclient"
BUNDLE_ID = "com.dmzs.datawatchclient"
ASC = "https://api.appstoreconnect.apple.com"
WEAR_OFFSET = 100000
IOS_OK = {"WAITING_FOR_BETA_REVIEW", "IN_BETA_REVIEW", "BETA_APPROVED", "IN_BETA_TESTING"}


def asc_session() -> requests.Session:
    key = base64.b64decode(os.environ["ASC_KEY_BASE64"]).decode()
    now = int(time.time())
    tok = jwt.encode({"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1100,
                      "aud": "appstoreconnect-v1"}, key, algorithm="ES256",
                     headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"})
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {tok}"
    return s


def ios_build(code: str):
    s = asc_session()
    app = s.get(f"{ASC}/v1/apps", params={"filter[bundleId]": BUNDLE_ID}, timeout=60).json()["data"][0]
    r = s.get(f"{ASC}/v1/builds", params={"filter[app]": app["id"], "filter[version]": code,
                                         "include": "buildBetaDetail"}, timeout=60).json()
    if not r.get("data"):
        return None, None
    b = r["data"][0]
    detail = next((i for i in r.get("included", []) if i["type"] == "buildBetaDetails"), None)
    return b["attributes"].get("processingState"), (detail or {}).get("attributes", {}).get("externalBuildState")


def wait_ios(code: str, timeout_min: int) -> int:
    deadline = time.time() + timeout_min * 60
    while time.time() < deadline:
        state, _ = ios_build(code)
        print(f"build {code}: processing={state}", flush=True)
        if state == "VALID":
            return 0
        if state in ("FAILED", "INVALID"):
            print(f"::error::TestFlight build {code} is {state}")
            return 1
        time.sleep(60)
    print(f"::error::TestFlight build {code} not processed after {timeout_min} min")
    return 1


def play_tracks() -> dict:
    import google.auth.transport.requests as gtr
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_KEY_JSON"]), scopes=["https://www.googleapis.com/auth/androidpublisher"])
    s = gtr.AuthorizedSession(creds)
    base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits"
    edit = s.post(base).json()["id"]
    try:
        tracks = s.get(f"{base}/{edit}/tracks").json().get("tracks", [])
    finally:
        s.delete(f"{base}/{edit}")
    return {t["track"]: {c for r in t.get("releases", []) if r.get("status") == "completed"
                         for c in r.get("versionCodes", [])} for t in tracks}


def verify(code: str) -> int:
    wear = str(WEAR_OFFSET + int(code))
    tracks = play_tracks()
    checks = [
        ("Play internal (phone)", code in tracks.get("internal", set())),
        ("Play closed testing (phone)", code in tracks.get("alpha", set())),
        ("Play internal (Wear)", wear in tracks.get("wear:internal", set())),
        ("Play closed testing (Wear)", wear in tracks.get("wear:Wear closed testing", set())),
    ]
    processing, external = ios_build(code)
    checks.append((f"TestFlight build (processing={processing}, external={external})",
                   processing == "VALID" and external in IOS_OK))
    for name, ok in checks:
        print(f"{'OK  ' if ok else 'FAIL'} {name}")
    behind = [n for n, ok in checks if not ok]
    if behind:
        print(f"::error::release parity: {len(behind)} channel(s) behind build {code}")
        return 1
    print(f"release parity: every channel carries build {code}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["wait-ios", "verify"])
    ap.add_argument("--code", required=True)
    ap.add_argument("--timeout-min", type=int, default=60)
    a = ap.parse_args()
    return wait_ios(a.code, a.timeout_min) if a.mode == "wait-ios" else verify(a.code)


if __name__ == "__main__":
    sys.exit(main())
