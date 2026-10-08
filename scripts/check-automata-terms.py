#!/usr/bin/env python3
"""AGENT.md § Terminology Rule: translated copy never keeps the English
"Automaton/Automata", never says "Automation" for the feature, and never "PRD".

Scans de/es/fr/ja Android string resources (phone, Auto, Wear), iOS .strings
values (app + widgets) and the App Store listings. Exit 1 with each offending
line. Usage: scripts/check-automata-terms.py
"""
import glob
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCALES = {"de": "de-DE", "es": "es-ES", "fr": "fr-FR", "ja": "ja"}
# Hyphen/underscore neighbours are identifiers (e.g. config key spawn-automaton).
ENGLISH = re.compile(r"(?<![-_\w])automat(a|on)s?(?![-_\w])", re.I)
WRONG = {
    "de": re.compile(r"Automatisierung"),
    "es": re.compile(r"automatizaci", re.I),
    "fr": re.compile(r"automatisation", re.I),
    "ja": re.compile(r"オートメーション"),
}
PRD = re.compile(r"(?<![-_\w])PRDs?(?![-_\w])")
IOS_VALUE = re.compile(r'^\s*".*?(?<!\\)"\s*=\s*"(.*)"\s*;\s*$')


def texts(lang: str):
    """Yield (file, line number, user-visible text) for one locale."""
    for f in sorted(glob.glob(str(ROOT / f"*/src/*/res/values-{lang}*/*.xml"))):
        for n, line in enumerate(open(f, encoding="utf-8"), 1):
            if "<!--" in line:  # XML comments are not user-visible
                continue
            yield f, n, re.sub(r'name="[^"]*"', "", line)
    for f in sorted(glob.glob(str(ROOT / f"iosApp/**/{lang}.lproj/*.strings"), recursive=True)):
        for n, line in enumerate(open(f, encoding="utf-8"), 1):
            m = IOS_VALUE.match(line)
            if m:
                yield f, n, m.group(1)
    for f in sorted(glob.glob(str(ROOT / f"iosApp/fastlane/metadata/{LOCALES[lang]}/*.txt"))):
        if f.endswith(("_url.txt", "name.txt")):
            continue
        for n, line in enumerate(open(f, encoding="utf-8"), 1):
            yield f, n, line


def main() -> int:
    bad = 0
    for lang in LOCALES:
        for f, n, text in texts(lang):
            if ENGLISH.search(text) or WRONG[lang].search(text) or PRD.search(text):
                print(f"{Path(f).relative_to(ROOT)}:{n}: {text.strip()[:160]}")
                bad += 1
    if bad:
        print(f"::error::{bad} translated line(s) break AGENT.md § Terminology Rule "
              "(use Automat/Automaten, autómata/autómatas, automate/automates, オートマトン/オートマタ)")
        return 1
    print("Automata terminology: all translated copy OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
