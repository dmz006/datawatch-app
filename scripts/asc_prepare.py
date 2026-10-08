#!/usr/bin/env python3
"""Prepare the App Store listing through the App Store Connect API.

Sets, idempotently:
  1. age rating declaration: every content category "none" (4+)
  2. content rights: does not use third-party content
  3. price: Free (base territory USA)
  4. availability: all territories, including new ones
  5. App Store version review details: contact + demo sign-in + notes
     (from env, like the TestFlight setup; never printed)

Nothing here submits or releases anything. Env:
  ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_BASE64 (the .p8, base64), BUNDLE_ID
  REVIEW_FIRST, REVIEW_LAST, REVIEW_PHONE, REVIEW_EMAIL, DEMO_URL, DEMO_TOKEN,
  REVIEW_NOTES_FILE (optional)
"""
import base64
import os
import sys
import time

import jwt
import requests

API = "https://api.appstoreconnect.apple.com"


def token() -> str:
    key = base64.b64decode(os.environ["ASC_KEY_BASE64"]).decode()
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
        key,
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"], "typ": "JWT"},
    )


S = requests.Session()
S.headers.update({"Authorization": f"Bearer {token()}", "Content-Type": "application/json"})


def call(method: str, path: str, **kw):
    r = S.request(method, API + path, timeout=60, **kw)
    if r.status_code >= 400:
        errs = r.json().get("errors", []) if r.headers.get("content-type", "").startswith("application/json") else []
        msg = "; ".join(f"{e.get('title')}: {e.get('detail')}" for e in errs) or r.text[:300]
        raise RuntimeError(f"{method} {path} -> {r.status_code}: {msg}")
    return r.json() if r.content else {}


def step(name, fn):
    try:
        print(f"OK   {name}: {fn()}")
        return True
    except Exception as e:  # report and continue with the next step
        print(f"FAIL {name}: {e}")
        return False


def main() -> int:
    app = call("GET", "/v1/apps", params={"filter[bundleId]": os.environ["BUNDLE_ID"]})["data"][0]
    app_id = app["id"]
    ok = True

    # 1. Age rating — on the editable appInfo.
    def age():
        infos = call("GET", f"/v1/apps/{app_id}/appInfos")["data"]
        info = next((i for i in infos if i["attributes"].get("appStoreState") not in ("READY_FOR_SALE",)), infos[0])
        decl = call("GET", f"/v1/appInfos/{info['id']}/ageRatingDeclaration")["data"]
        none_enum = [
            "alcoholTobaccoOrDrugUseOrReferences", "contests", "gamblingSimulated", "gunsOrOtherWeapons",
            "horrorOrFearThemes", "matureOrSuggestiveThemes", "medicalOrTreatmentInformation",
            "profanityOrCrudeHumor", "sexualContentGraphicAndNudity", "sexualContentOrNudity",
            "violenceCartoonOrFantasy", "violenceRealistic", "violenceRealisticProlongedGraphicOrSadistic",
        ]
        false_bool = [
            "gambling", "lootBox", "unrestrictedWebAccess", "advertising", "ageAssurance",
            "healthOrWellnessTopics", "messagingAndChat", "parentalControls", "userGeneratedContent",
        ]
        known = set(decl["attributes"].keys())
        attrs = {k: "NONE" for k in none_enum if k in known}
        attrs.update({k: False for k in false_bool if k in known})
        call("PATCH", f"/v1/ageRatingDeclarations/{decl['id']}",
             json={"data": {"type": "ageRatingDeclarations", "id": decl["id"], "attributes": attrs}})
        return f"{len(attrs)} answers set (all none / no)"

    ok &= step("age rating", age)

    # 2. Content rights.
    ok &= step("content rights", lambda: (call("PATCH", f"/v1/apps/{app_id}", json={"data": {
        "type": "apps", "id": app_id,
        "attributes": {"contentRightsDeclaration": "DOES_NOT_USE_THIRD_PARTY_CONTENT"}}}), "does not use third-party content")[1])

    # 3. Price: Free.
    def price():
        points = call("GET", f"/v1/apps/{app_id}/appPricePoints",
                      params={"filter[territory]": "USA", "limit": 200})["data"]
        free = next(p for p in points if float(p["attributes"].get("customerPrice", "1")) == 0.0)
        call("POST", "/v1/appPriceSchedules", json={
            "data": {"type": "appPriceSchedules", "relationships": {
                "app": {"data": {"type": "apps", "id": app_id}},
                "baseTerritory": {"data": {"type": "territories", "id": "USA"}},
                "manualPrices": {"data": [{"type": "appPrices", "id": "${price-free}"}]}}},
            "included": [{"type": "appPrices", "id": "${price-free}",
                          "attributes": {"startDate": None},
                          "relationships": {"appPricePoint": {"data": {"type": "appPricePoints", "id": free["id"]}}}}],
        })
        return "Free (USA base)"

    ok &= step("price", price)

    # 4. Availability: every territory + new ones.
    def availability():
        terr = []
        url = "/v1/territories?limit=200"
        while url:
            page = call("GET", url)
            terr += [t["id"] for t in page["data"]]
            nxt = page.get("links", {}).get("next")
            url = nxt.replace(API, "") if nxt else None
        inc = [{"type": "territoryAvailabilities", "id": f"${{t-{t}}}", "attributes": {"available": True}, "relationships": {
            "territory": {"data": {"type": "territories", "id": t}}}} for t in terr]
        call("POST", "/v2/appAvailabilities", json={
            "data": {"type": "appAvailabilities", "attributes": {"availableInNewTerritories": True},
                     "relationships": {"app": {"data": {"type": "apps", "id": app_id}},
                                       "territoryAvailabilities": {"data": [{"type": "territoryAvailabilities", "id": i["id"]} for i in inc]}}},
            "included": inc,
        })
        return f"{len(terr)} territories + future ones"

    ok &= step("availability", availability)

    # 5. App Store version review details (contact + demo sign-in + notes).
    def review():
        vers = call("GET", f"/v1/apps/{app_id}/appStoreVersions",
                    params={"filter[appStoreState]": "PREPARE_FOR_SUBMISSION,DEVELOPER_REJECTED,REJECTED,METADATA_REJECTED"})["data"]
        if not vers:
            return "no editable App Store version yet (skipped)"
        v = vers[0]
        notes_file = os.environ.get("REVIEW_NOTES_FILE", "")
        attrs = {k: v_ for k, v_ in {
            "contactFirstName": os.environ.get("REVIEW_FIRST"), "contactLastName": os.environ.get("REVIEW_LAST"),
            "contactPhone": os.environ.get("REVIEW_PHONE"), "contactEmail": os.environ.get("REVIEW_EMAIL"),
            "demoAccountName": os.environ.get("DEMO_URL"), "demoAccountPassword": os.environ.get("DEMO_TOKEN"),
            "demoAccountRequired": True,
            "notes": open(notes_file).read().strip() if notes_file and os.path.exists(notes_file) else None,
        }.items() if v_}
        cur = S.get(API + f"/v1/appStoreVersions/{v['id']}/appStoreReviewDetail", timeout=60)
        if cur.status_code == 200 and cur.json().get("data"):
            rid = cur.json()["data"]["id"]
            call("PATCH", f"/v1/appStoreReviewDetails/{rid}",
                 json={"data": {"type": "appStoreReviewDetails", "id": rid, "attributes": attrs}})
        else:
            call("POST", "/v1/appStoreReviewDetails", json={"data": {
                "type": "appStoreReviewDetails", "attributes": attrs,
                "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
        return f"version {v['attributes'].get('versionString')}: contact + demo sign-in + notes set"

    ok &= step("App Store review details", review)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
