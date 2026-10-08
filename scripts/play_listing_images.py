#!/usr/bin/env python3
"""Replace one image set on the Play store listing (no release, no re-upload of bundles).

Deletes every image of --type for --language, uploads the PNGs in --dir (sorted
by name), then commits the edit. Listing changes go through Google's review
like any other change.

Env: PLAY_KEY_JSON (service-account JSON). Usage:
  play_listing_images.py --package com.dmzs.datawatchclient --language en-US \
      --type wearScreenshots --dir docs/media/watch/store
"""
import argparse
import json
import os
import sys
from pathlib import Path

import google.auth.transport.requests as gtr
from google.oauth2 import service_account

# Play allows at most 8 screenshots per image type and language.
MAX_IMAGES = 8
TYPES = {"phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots", "wearScreenshots", "tvScreenshots"}


def _err(r) -> str:
    """API error message without braces (GitHub masks a lone "{" from the key JSON)."""
    try:
        e = r.json().get("error", {})
        return f"{r.status_code} {e.get('status', '')}: {e.get('message', '')}"
    except ValueError:
        return f"{r.status_code} {r.text[:300]}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", required=True)
    ap.add_argument("--language", default="en-US")
    ap.add_argument("--type", required=True, choices=sorted(TYPES))
    ap.add_argument("--dir", required=True)
    a = ap.parse_args()

    files = sorted(Path(a.dir).glob("*.png"))
    if not files:
        print(f"::error::no PNGs in {a.dir}")
        return 1
    if len(files) > MAX_IMAGES:
        print(f"::error::{len(files)} PNGs in {a.dir}; Play allows {MAX_IMAGES} per image type")
        return 1
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_KEY_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    s = gtr.AuthorizedSession(creds)
    api = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
    upload = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
    edit = s.post(f"{api}/{a.package}/edits").json()["id"]
    path = f"{a.package}/edits/{edit}/listings/{a.language}/{a.type}"
    committed = False
    try:
        r = s.delete(f"{api}/{path}")
        if not r.ok:
            print(f"::error::delete existing {a.type} failed: {_err(r)}")
            return 1
        for f in files:
            r = s.post(f"{upload}/{path}?uploadType=media", data=f.read_bytes(),
                       headers={"Content-Type": "image/png"})
            if not r.ok:
                print(f"::error::upload {f.name} failed: {_err(r)}")
                return 1
            print(f"uploaded {f.name}")
        c = s.post(f"{api}/{a.package}/edits/{edit}:commit")
        if not c.ok:
            print(f"::error::commit failed: {_err(c)}")
            return 1
        committed = True
        print(f"Replaced {a.type} ({a.language}) with {len(files)} image(s)")
        return 0
    finally:
        if not committed:
            s.delete(f"{api}/{a.package}/edits/{edit}")


if __name__ == "__main__":
    sys.exit(main())
