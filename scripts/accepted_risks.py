"""Shared loader + validator for security/accepted-risks.yml.

Security acceptance standard (operator, 2026-10-08; shared with datawatch,
dmz006/datawatch#197) — see AGENT.md § "Security acceptance standard". Used by
scripts/check_accepted_risks.py (CI lint) and scripts/sca_fix_watch.py (daily
watch). Standard library only: the registry is read with a small YAML-subset
parser (block maps, block lists, folded/literal scalars, quoted scalars,
true/false, [] / {} and flow lists [a, b]), so CI needs no pip install.
"""
import datetime
import re

REQUIRED = ("id", "kind", "package", "severity", "impact", "first_added", "added", "expires", "validated_by", "reason")
KINDS = ("container", "dependency", "code-scanning", "bundled-js")
# `version` (the accepted version) is required for these kinds; code-scanning
# entries carry `path` instead (version "<path>@<sha-short>" is optional there).
VERSIONED_KINDS = ("dependency", "bundled-js", "container")
SEVERITIES = ("low", "medium", "moderate", "high", "critical")
REACHABLE = ("yes", "no", "unknown")
MAX_DAYS_TRACED = 90
MAX_DAYS_UNTRACED = 30
ID_RE = re.compile(r"^(dependabot/\d+|code-scanning/\d+|osv/[A-Za-z0-9][A-Za-z0-9._:-]*|cve/CVE-\d{4}-\d{4,})$")
LOGIN_RE = re.compile(r"^(claude-session|[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38})$")
MAVEN_RE = re.compile(r"^[A-Za-z0-9_.\-]+:[A-Za-z0-9_.\-]+$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


# --------------------------------------------------------------------------- YAML subset

def _scalar(s):
    s = s.strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in "\"'":
        return s[1:-1]
    s = re.sub(r"\s+#.*$", "", s)  # trailing comment on an unquoted scalar
    if s in ("true", "false"):
        return s == "true"
    if s == "[]":
        return []
    if s.startswith("[") and s.endswith("]"):
        return [_scalar(x) for x in s[1:-1].split(",") if x.strip()]
    if s == "{}":
        return {}
    if s in ("null", "~", ""):
        return None
    return s


def _lines(text):
    # Drops blank and comment lines — except inside a block scalar (>-, |),
    # where a line starting with "#" (e.g. "#14: ...") is text.
    out, block_indent = [], None
    for raw in text.splitlines():
        if not raw.strip():
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        if block_indent is not None and indent > block_indent:
            out.append((indent, raw.strip()))
            continue
        block_indent = None
        if raw.lstrip().startswith("#"):
            continue
        out.append((indent, raw.strip()))
        if re.search(r":\s+[>|]-?$", raw):
            # a block scalar's lines are deeper than its key ("- key:" counts at the key)
            block_indent = indent + 2 if raw.strip().startswith("- ") else indent
    return out


def _parse(lines, i, indent):
    if i < len(lines) and lines[i][0] == indent and (lines[i][1] == "-" or lines[i][1].startswith("- ")):
        items = []
        while i < len(lines) and lines[i][0] == indent and (lines[i][1] == "-" or lines[i][1].startswith("- ")):
            rest = lines[i][1][1:].strip()
            if not rest:
                val, i = _parse(lines, i + 1, lines[i + 1][0]) if i + 1 < len(lines) else (None, i + 1)
                items.append(val)
                continue
            if re.match(r"^[\w.\-]+:(\s|$)", rest):
                lines = lines[:i] + [(indent + 2, rest)] + lines[i + 1:]
                val, i = _parse(lines, i, indent + 2)
            else:
                val, i = _scalar(rest), i + 1
            items.append(val)
        return items, i
    out = {}
    while i < len(lines) and lines[i][0] == indent:
        m = re.match(r"^([\w.\-]+):(?:\s+(.*))?$", lines[i][1])
        if not m:
            raise ValueError("cannot parse line: %r" % lines[i][1])
        k, rest = m.group(1), (m.group(2) or "").strip()
        i += 1
        if rest in (">", ">-", "|", "|-"):
            parts = []
            while i < len(lines) and lines[i][0] > indent:
                parts.append(lines[i][1])
                i += 1
            out[k] = ("\n" if rest.startswith("|") else " ").join(parts)
        elif rest == "" and i < len(lines) and (
            lines[i][0] > indent or (lines[i][0] == indent and lines[i][1].startswith("- "))
        ):
            out[k], i = _parse(lines, i, lines[i][0])
        else:
            out[k] = _scalar(rest)
    return out, i


def loads(text):
    lines = _lines(text)
    if not lines:
        return {}
    doc, i = _parse(lines, 0, lines[0][0])
    if i != len(lines):
        raise ValueError("unparsed content at: %r" % lines[i][1])
    return doc


def load(path):
    """Return the list of registry entries (dicts)."""
    with open(path, encoding="utf-8") as f:
        doc = loads(f.read())
    risks = (doc or {}).get("risks") or []
    if not isinstance(risks, list):
        raise ValueError("`risks` must be a list")
    return risks


# --------------------------------------------------------------------------- helpers

def parse_date(v):
    """YYYY-MM-DD string or date -> date, else None."""
    if isinstance(v, datetime.date):
        return v
    if isinstance(v, str) and DATE_RE.match(v.strip()):
        try:
            return datetime.date.fromisoformat(v.strip())
        except ValueError:
            return None
    return None


def norm_reachable(v):
    # PyYAML 1.1 turns unquoted yes/no into booleans; accept either spelling.
    if v is True:
        return "yes"
    if v is False:
        return "no"
    return str(v).strip().lower() if v is not None else None


def max_days(entry):
    """Expiry window for the entry: 90 days when traced, 30 when not."""
    impact = entry.get("impact") if isinstance(entry.get("impact"), dict) else {}
    return MAX_DAYS_TRACED if impact.get("traced") is True else MAX_DAYS_UNTRACED


def needs_escalation(entry):
    """Operator escalation: reachable=yes on a HIGH/CRITICAL finding."""
    impact = entry.get("impact") if isinstance(entry.get("impact"), dict) else {}
    return str(entry.get("severity", "")).lower() in ("high", "critical") and norm_reachable(impact.get("reachable")) == "yes"


def renewed_past_first_expiry(entry, today):
    """Operator escalation: the entry was renewed (added > first_added) and is
    now past its first expiry window (first_added + 90 d traced / 30 d not)."""
    first, added = parse_date(entry.get("first_added")), parse_date(entry.get("added"))
    if not first or not added or added <= first:
        return False
    return today > first + datetime.timedelta(days=max_days(entry))


def escalations(entry, today):
    """-> list of operator-escalation reasons (empty when none)."""
    out = []
    if needs_escalation(entry):
        out.append("reachable=yes on a %s finding" % entry.get("severity"))
    if renewed_past_first_expiry(entry, today):
        first = parse_date(entry.get("first_added"))
        out.append("renewed past its first expiry (first_added %s + %d d = %s)" % (
            first, max_days(entry), first + datetime.timedelta(days=max_days(entry))))
    return out


# --------------------------------------------------------------------------- validation

def validate(risks, today=None):
    """Return (errors, escalations) — lists of strings. Escalations never fail
    the lint; they are printed as ESCALATE lines for the operator."""
    today = today or datetime.date.today()
    errors, warnings, seen = [], [], set()
    for n, e in enumerate(risks):
        if not isinstance(e, dict):
            errors.append("entry #%d: not a map" % (n + 1))
            continue
        rid = e.get("id") or "entry #%d" % (n + 1)
        err = lambda msg: errors.append("%s: %s" % (rid, msg))  # noqa: E731
        for f in REQUIRED:
            if e.get(f) in (None, "", {}, []):
                err("missing required field `%s`" % f)
        if e.get("id"):
            if not ID_RE.match(str(e["id"])):
                err("id must be dependabot/<n>, code-scanning/<n>, osv/<ID> or cve/<CVE-ID>")
            if e["id"] in seen:
                err("duplicate id")
            seen.add(e["id"])
        kind = e.get("kind")
        if kind and kind not in KINDS:
            err("kind must be one of %s" % "|".join(KINDS))
        if kind in VERSIONED_KINDS and e.get("version") in (None, ""):
            err("missing required field `version` (the accepted version; required for %s)" % kind)
        if kind == "code-scanning" and e.get("path") in (None, ""):
            err("missing required field `path` (the alert's file; required for code-scanning)")
        if e.get("images") is not None and not (
            isinstance(e["images"], list) and all(isinstance(x, str) and x for x in e["images"])
        ):
            err("images must be a list of image names")
        if kind == "dependency" and e.get("package") and not MAVEN_RE.match(str(e["package"])):
            err("dependency package must be Maven coordinates group:artifact")
        if e.get("severity") and str(e["severity"]).lower() not in SEVERITIES:
            err("severity must be one of %s" % "|".join(SEVERITIES))
        if e.get("validated_by") and not LOGIN_RE.match(str(e["validated_by"])):
            err("validated_by must be a GitHub login or `claude-session`")

        impact = e.get("impact")
        traced = None
        if impact not in (None, "", {}):
            if not isinstance(impact, dict):
                err("impact must be a map {traced, reachable, analysis}")
            else:
                traced = impact.get("traced")
                if not isinstance(traced, bool):
                    err("impact.traced must be true or false")
                    traced = None
                if norm_reachable(impact.get("reachable")) not in REACHABLE:
                    err("impact.reachable must be yes|no|unknown%s" % (
                        " (required when traced is false)" if traced is False else ""))
                if traced is True and not str(impact.get("method") or "").strip():
                    err("impact.method is required when traced is true (how the code path was traced)")
                if not str(impact.get("analysis") or "").strip():
                    err("impact.analysis is required%s" % (" (required when traced is false)" if traced is False else ""))

        dates = {}
        for f in ("first_added", "added", "expires"):
            if e.get(f) not in (None, ""):
                d = parse_date(e[f])
                if d is None:
                    err("`%s` must be a valid YYYY-MM-DD date" % f)
                else:
                    dates[f] = d
        first, added, expires = dates.get("first_added"), dates.get("added"), dates.get("expires")
        if added and added > today:
            err("added %s is in the future" % added)
        if first and added and first > added:
            err("first_added %s is after added %s (first_added is the first acceptance and never changes)" % (first, added))
        if added and expires:
            if expires <= added:
                err("expires must be after added")
            limit = MAX_DAYS_TRACED if traced is not False else MAX_DAYS_UNTRACED
            if traced is not None and (expires - added).days > limit:
                err("expires %s is more than %d days after added %s (%s)" % (
                    expires, limit, added, "traced" if traced else "not traced"))
        if expires and expires < today:
            err("expired on %s — re-review (new impact analysis + new dates) or remove the entry" % expires)
        for why in escalations(e, today):
            warnings.append("%s: %s — escalate to the operator" % (rid, why))
    return errors, warnings


def first_added_changes(old_risks, new_risks):
    """first_added is immutable: -> list of "id: old -> new" for entries present
    in both versions of the registry whose first_added changed."""
    old = {str(r.get("id")): r.get("first_added") for r in old_risks if isinstance(r, dict)}
    out = []
    for r in new_risks:
        if not isinstance(r, dict) or str(r.get("id")) not in old:
            continue
        before = old[str(r.get("id"))]
        if before not in (None, "") and str(before) != str(r.get("first_added")):
            out.append("%s: first_added changed %s -> %s" % (r.get("id"), before, r.get("first_added")))
    return out
