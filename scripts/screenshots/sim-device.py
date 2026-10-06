#!/usr/bin/env python3
"""Print the UDID of an available simulator with the given name, creating it
(newest iOS runtime) when absent.  Usage: sim-device.py "iPhone 17 Pro Max"
"""
import json
import subprocess
import sys


def simctl_json(*args):
    return json.loads(subprocess.check_output(["xcrun", "simctl", "list", "-j", *args]))


def runtime_version(rt):
    return tuple(int(p) for p in rt.get("version", "0").split(".") if p.isdigit())


def main(name):
    ios_runtimes = sorted(
        (r for r in simctl_json("runtimes")["runtimes"]
         if r.get("isAvailable") and r.get("platform", "iOS") == "iOS" and "iOS" in r.get("name", "")),
        key=runtime_version, reverse=True,
    )
    if not ios_runtimes:
        sys.exit("no available iOS simulator runtime")
    devices = simctl_json("devices", "available")["devices"]
    for rt in ios_runtimes:
        for d in devices.get(rt["identifier"], []):
            if d["name"] == name:
                print(d["udid"])
                return
    dtypes = simctl_json("devicetypes")["devicetypes"]
    match = [t for t in dtypes if t["name"] == name] or [t for t in dtypes if t["name"].startswith(name)]
    if not match:
        sys.exit(f"no device type named {name!r}; have: {[t['name'] for t in dtypes]}")
    rt = ios_runtimes[0]
    udid = subprocess.check_output(
        ["xcrun", "simctl", "create", name, match[0]["identifier"], rt["identifier"]], text=True
    ).strip()
    print(f"created {name} ({match[0]['identifier']}, {rt['name']}) -> {udid}", file=sys.stderr)
    print(udid)


if __name__ == "__main__":
    main(sys.argv[1])
