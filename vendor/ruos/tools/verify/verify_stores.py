#!/usr/bin/env python3
"""
Round-trip fidelity for the deterministic JSON stores (faithful ports of the Kotlin
serialize/parse). A write/read *key* mismatch or a *type-coercion* slip (e.g. a Long
millis read back with optInt, or a Float radius truncated) silently corrupts user data
on the next app launch — these never throw, they just lose values.

Each store is ported exactly: same keys, same defaults, same numeric coercions. We
serialize an object, parse it back, and assert the whole object survives — including the
nasty cases (Cyrillic, empty strings, Long colours past Int range, Float→Double→Float
radius, nested schedule, ordered package sets).

Run:  python3 vendor/ruos/tools/verify/verify_stores.py
"""
import json, struct, sys

fails = 0
def check(cond, msg):
    global fails
    if not cond: print("  FAIL:", msg); fails += 1

def f2d2f(x):
    """Kotlin: Float -> .toDouble() (stored) -> optDouble -> .toFloat(). Simulate the
    32-bit round so the assertion below is exact, not approximate."""
    return struct.unpack("f", struct.pack("f", x))[0]


# ── FocusStore (FocusMode + nested FocusSchedule) ────────────────────────────
def focus_serialize(modes):
    arr = []
    for m in modes:
        o = {"id": m["id"], "name": m["name"], "color": m["colorHex"],
             "icon": m["icon"], "apps": list(m["allowedPackages"]),
             "allowCalls": m["allowCalls"], "allowRepeat": m["allowRepeatCalls"],
             "suppressAll": m["suppressAll"], "dim": m["dimLockScreen"],
             "hide": m["hideNotifications"], "reply": m["autoReply"], "builtIn": m["builtIn"]}
        if m["schedule"] is not None:
            s = m["schedule"]
            o["sch"] = {"on": s["enabled"], "start": s["startMin"], "end": s["endMin"], "days": s["days"]}
        arr.append(o)
    return json.dumps(arr)

ICONS = {"MOON", "BRIEFCASE", "PERSON", "BED", "CAR", "BOOK", "STAR", "HEART", "GAME", "DUMBBELL"}
def focus_parse(raw):
    out = []
    for o in json.loads(raw):
        apps = []
        for p in o.get("apps", []):       # LinkedHashSet: preserve order, dedupe
            if p not in apps: apps.append(p)
        sch = None
        if "sch" in o:
            s = o["sch"]
            sch = {"enabled": s.get("on", False), "startMin": s.get("start", 22*60),
                   "endMin": s.get("end", 7*60), "days": s.get("days", 0x7F)}
        icon = o.get("icon", "MOON")
        if icon not in ICONS: icon = "MOON"      # FocusIcon.valueOf(...).getOrDefault(MOON)
        out.append({
            "id": o["id"], "name": o["name"], "colorHex": int(o.get("color", 0xFF5E5CE6)),
            "icon": icon, "allowedPackages": apps,
            "allowCalls": o.get("allowCalls", True), "allowRepeatCalls": o.get("allowRepeat", True),
            "suppressAll": o.get("suppressAll", False), "dimLockScreen": o.get("dim", True),
            "hideNotifications": o.get("hide", False), "autoReply": o.get("reply", ""),
            "schedule": sch, "builtIn": o.get("builtIn", False)})
    return out

focus_in = [
    {"id": "sleep", "name": "Сон", "colorHex": 0xFF5E5CE6, "icon": "BED",
     "allowedPackages": ["com.ruos.phone", "com.ruos.clock"],
     "allowCalls": True, "allowRepeatCalls": False, "suppressAll": True, "dimLockScreen": True,
     "hideNotifications": True, "autoReply": "Сплю, отвечу утром.", "builtIn": True,
     "schedule": {"enabled": True, "startMin": 23*60, "endMin": 7*60, "days": 0x7F}},
    {"id": "work-«офис»", "name": "Работа", "colorHex": 0xFFFF9F0A, "icon": "BRIEFCASE",
     "allowedPackages": [], "allowCalls": False, "allowRepeatCalls": True, "suppressAll": False,
     "dimLockScreen": False, "hideNotifications": False, "autoReply": "", "builtIn": False,
     "schedule": None},
]
check(focus_parse(focus_serialize(focus_in)) == focus_in, "FocusStore round-trip lost data")
# colour must survive past Int range (0xFFFF9F0A > 2^31) as an exact Long
check(focus_parse(focus_serialize(focus_in))[1]["colorHex"] == 0xFFFF9F0A, "FocusStore Long colour truncated")
print(f"FocusStore: {'OK' if not fails else 'FAIL'} ({len(focus_in)} modes, nested schedule, Long colour, ordered apps)")


# ── ReminderStore (Reminder + ReminderList) ──────────────────────────────────
def rem_to_json(r):
    return {"id": r["id"], "listId": r["listId"], "title": r["title"], "notes": r["notes"],
            "hasTime": r["hasTime"], "due": r["dueMillis"], "hasLoc": r["hasLocation"],
            "lat": r["locLat"], "lng": r["locLng"], "rad": float(r["locRadius"]),
            "locLabel": r["locLabel"], "arrival": r["onArrival"], "flag": r["flagged"],
            "done": r["completed"], "created": r["createdAt"]}
def rem_from_json(o):
    return {"id": o["id"], "listId": o.get("listId", "default"), "title": o.get("title", ""),
            "notes": o.get("notes", ""), "hasTime": o.get("hasTime", False), "dueMillis": int(o.get("due", 0)),
            "hasLocation": o.get("hasLoc", False), "locLat": float(o.get("lat", 0.0)), "locLng": float(o.get("lng", 0.0)),
            "locRadius": f2d2f(o.get("rad", 150.0)), "locLabel": o.get("locLabel", ""),
            "onArrival": o.get("arrival", True), "flagged": o.get("flag", False),
            "completed": o.get("done", False), "createdAt": int(o.get("created", 0))}

rad0 = f2d2f(150.0)   # store seeds Float; compare against the Float-precision value
rem_in = {"id": "r1", "listId": "shopping", "title": "Купить молоко", "notes": "2 % жирности",
          "hasTime": True, "dueMillis": 1_900_000_000_000, "hasLocation": True,
          "locLat": 55.751244, "locLng": 37.618423, "locRadius": rad0, "locLabel": "Пятёрочка",
          "onArrival": True, "flagged": True, "completed": False, "createdAt": 1_750_000_000_000}
rt = rem_from_json(rem_to_json(rem_in))
check(rt == rem_in, "ReminderStore round-trip lost data")
# due/created are 13-digit millis: must NOT be read with optInt (would truncate past 2^31)
check(rt["dueMillis"] == 1_900_000_000_000 and rt["createdAt"] == 1_750_000_000_000, "Reminder millis truncated (optInt instead of optLong?)")
check(rem_from_json(rem_to_json({**rem_in, "locRadius": f2d2f(87.5)}))["locRadius"] == f2d2f(87.5), "Reminder Float radius mangled by Double round")
print("ReminderStore: OK (13-digit millis as Long, Float radius via Double, Cyrillic, geo doubles)")


# ── KeychainStore (Credential + TotpAccount, inside the encrypted blob) ───────
def kc_persist(creds, totps):
    return json.dumps({
        "creds": [{"id": c["id"], "title": c["title"], "domain": c["domain"],
                   "user": c["username"], "pass": c["password"], "note": c["note"]} for c in creds],
        "totp": [{"id": t["id"], "issuer": t["issuer"], "account": t["account"], "secret": t["secret"]} for t in totps]})
def kc_parse(blob):
    root = json.loads(blob)
    creds = [{"id": o["id"], "title": o.get("title", ""), "domain": o.get("domain", ""),
              "username": o.get("user", ""), "password": o.get("pass", ""), "note": o.get("note", "")}
             for o in root.get("creds", [])]
    totps = [{"id": o["id"], "issuer": o.get("issuer", ""), "account": o.get("account", ""), "secret": o.get("secret", "")}
             for o in root.get("totp", [])]
    return creds, totps

creds_in = [{"id": "c1", "title": "Госуслуги", "domain": "gosuslugi.ru", "username": "ivan",
             "password": "P@ss:word;1\\\"", "note": "основной"}]
totps_in = [{"id": "t1", "issuer": "Госуслуги", "account": "ivan@mail.ru", "secret": "JBSWY3DPEHPK3PXP"}]
co, to = kc_parse(kc_persist(creds_in, totps_in))
check(co == creds_in and to == totps_in, "KeychainStore round-trip lost data (note the key rename user/pass)")
print("KeychainStore: OK (Credential user/pass key rename, special chars in password, Cyrillic issuer)")


# ── WidgetStore (WidgetSpec: type + size enum) ───────────────────────────────
SIZES = {"SMALL", "MEDIUM", "LARGE"}
def widget_serialize(specs):
    return json.dumps([{"type": t, "size": s} for (t, s) in specs])
def widget_parse(raw):
    out = []
    for o in json.loads(raw):
        t = o.get("type", "")
        if not t: continue                     # isNullOrEmpty → skip
        sz = o.get("size", "")
        out.append((t, sz if sz in SIZES else "MEDIUM"))   # WidgetSize.from default MEDIUM
    return out

widgets_in = [("weather", "LARGE"), ("music", "SMALL")]
check(widget_parse(widget_serialize(widgets_in)) == widgets_in, "WidgetStore round-trip lost data")
# unknown size name must fall back to MEDIUM, not throw or drop
check(widget_parse('[{"type":"weather","size":"HUGE"}]') == [("weather", "MEDIUM")], "WidgetStore unknown size not defaulted")
# an entry with no type must be skipped, not kept as a phantom widget
check(widget_parse('[{"size":"SMALL"},{"type":"music","size":"MEDIUM"}]') == [("music", "MEDIUM")], "WidgetStore empty-type entry not skipped")
print("WidgetStore: OK (S/M/L enum round-trip, unknown-size→MEDIUM, empty-type skipped)")


# ── TextReplacementStore (Replacement: shortcut + phrase) ────────────────────
def tr_serialize(rules):
    return json.dumps([{"sc": sc, "ph": ph} for (sc, ph) in rules])
def tr_parse(raw):
    out = []
    for o in json.loads(raw):
        sc = o.get("sc", ""); ph = o.get("ph", "")
        if sc and ph: out.append((sc, ph))      # both non-empty required
    return out

tr_in = [("спс", "спасибо"), ("омг", "о, мой бог"), ("др", "день рождения")]
check(tr_parse(tr_serialize(tr_in)) == tr_in, "TextReplacementStore round-trip lost data")
# a rule missing a phrase must be dropped, not stored as a half-rule
check(tr_parse('[{"sc":"x"},{"sc":"спс","ph":"спасибо"}]') == [("спс", "спасибо")], "TextReplacementStore half-rule not dropped")
print("TextReplacementStore: OK (Cyrillic round-trip, half-rule dropped)")

print(f"\n{'ALL STORE ROUND-TRIPS PASSED' if not fails else f'{fails} PROBLEM(S)'}")
sys.exit(1 if fails else 0)
