#!/usr/bin/env python3
"""
Correctness checks for RuOS pure-logic helpers (faithful ports of the Kotlin):
  * FocusSchedule.contains  — time/day windows incl. overnight (Сон 23:00→07:00)
  * Totp.parseUri           — otpauth:// parsing (label + issuer URL-decoding)
  * QrCodes.wifiPayload      — WIFI: string escaping

Run:  python3 vendor/ruos/tools/verify/verify_logic.py
"""
import sys
from urllib.parse import unquote

fails = 0

# ── FocusSchedule.contains ────────────────────────────────────────────────────
def contains(enabled, start, end, days, now, today):
    if not enabled: return False
    overnight = end <= start
    if not overnight:
        return ((days >> today) & 1 == 1) and start <= now < end
    if now >= start: return (days >> today) & 1 == 1
    return ((days >> ((today + 6) % 7)) & 1 == 1) and now < end

def H(h, m=0): return h * 60 + m
ALL, WEEK = 0x7F, 0b0011111
sleep = (True, H(23), H(7), ALL); work = (True, H(9), H(18), WEEK)
focus_cases = [
    (sleep, H(23,30), 0, True), (sleep, H(6), 1, True), (sleep, H(8), 1, False),
    (sleep, H(22), 0, False), (sleep, H(0), 0, True),
    (work, H(12), 2, True), (work, H(8), 2, False), (work, H(18), 2, False), (work, H(12), 5, False),
]
n = 0
for sch, now, day, exp in focus_cases:
    if contains(*sch, now, day) != exp: print(f"FOCUS FAIL now={now} day={day} exp={exp}"); fails += 1; n += 1
print(f"FocusSchedule.contains: {len(focus_cases)-n}/{len(focus_cases)}")

# ── Totp.parseUri ─────────────────────────────────────────────────────────────
def parse_uri(uri):
    if not uri.startswith("otpauth://totp/"): return None
    rest = uri[len("otpauth://totp/"):]
    label = rest.split("?", 1)[0]; query = rest.split("?", 1)[1] if "?" in rest else ""
    params = {}
    for kv in query.split("&"):
        p = kv.split("=", 1)
        if len(p) == 2: params[p[0]] = p[1]
    if "secret" not in params: return None
    dl = unquote(label)
    issuer = unquote(params["issuer"]) if "issuer" in params else (dl.split(":", 1)[0] if ":" in dl else dl)
    account = dl.split(":", 1)[1] if ":" in dl else dl
    return (issuer.strip(), account.strip(), params["secret"].strip())

parse_cases = [
    ("otpauth://totp/ACME%20Co:john@mail?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co", ("ACME Co", "john@mail", "JBSWY3DPEHPK3PXP")),
    ("otpauth://totp/x:user?secret=AAAA&issuer=%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8", ("Госуслуги", "user", "AAAA")),
    ("otpauth://totp/%D0%92%D0%9A:me?secret=BBBB", ("ВК", "me", "BBBB")),
    ("https://bad", None), ("otpauth://totp/x?foo=bar", None),
]
n = 0
for uri, exp in parse_cases:
    if parse_uri(uri) != exp: print(f"PARSE FAIL {uri[:40]}"); fails += 1; n += 1
print(f"Totp.parseUri: {len(parse_cases)-n}/{len(parse_cases)}")

# ── QrCodes.wifiPayload ───────────────────────────────────────────────────────
def esc(s): return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace('"', '\\"')
def wifi_payload(ssid, password, wpa):
    return f"WIFI:T:WPA;S:{esc(ssid)};P:{esc(password)};;" if (wpa and password) else f"WIFI:T:nopass;S:{esc(ssid)};;"
wifi_cases = [
    (("RuOS", "parol", True), "WIFI:T:WPA;S:RuOS;P:parol;;"),
    (("Net;1", "a:b", True), "WIFI:T:WPA;S:Net\\;1;P:a\\:b;;"),
    (("Open", "", False), "WIFI:T:nopass;S:Open;;"),
]
n = 0
for a, exp in wifi_cases:
    if wifi_payload(*a) != exp: print(f"WIFI FAIL {a}"); fails += 1; n += 1
print(f"QrCodes.wifiPayload: {len(wifi_cases)-n}/{len(wifi_cases)}")

# ── HotspotQrActivity.parseArpClients ─────────────────────────────────────────
def parse_arp(content):
    out = {}                                   # MAC -> (ip, mac, iface), keep first
    for line in content.splitlines()[1:]:      # drop header row
        f = line.strip().split()
        if len(f) < 6: continue
        ip, flags, mac, iface = f[0], f[2], f[3], f[5]
        try: fv = int(flags[2:], 16) if flags.startswith("0x") else int(flags)
        except ValueError: fv = 0
        if fv & 0x2 == 0: continue             # only complete (ATF_COM) entries
        if mac.lower() == "00:00:00:00:00:00" or mac.count(":") != 5: continue
        out.setdefault(mac.lower(), (ip, mac.lower(), iface))
    return list(out.values())

ARP = """IP address       HW type     Flags       HW address            Mask     Device
192.168.43.100   0x1         0x2         a4:50:46:11:22:33     *        ap0
192.168.43.101   0x1         0x2         de:ad:be:ef:00:01     *        ap0
192.168.43.102   0x1         0x0         00:00:00:00:00:00     *        ap0
192.168.43.100   0x1         0x2         a4:50:46:11:22:33     *        ap0
10.0.0.1         0x1         0x6         b8:27:eb:aa:bb:cc     *        wlan0"""
got = parse_arp(ARP)
exp = [("192.168.43.100", "a4:50:46:11:22:33", "ap0"),
       ("192.168.43.101", "de:ad:be:ef:00:01", "ap0"),
       ("10.0.0.1", "b8:27:eb:aa:bb:cc", "wlan0")]   # incomplete dropped, dupe deduped, 0x6 kept (0x4|0x2)
if got != exp: print(f"ARP FAIL\n  got {got}\n  exp {exp}"); fails += 1
print(f"HotspotQrActivity.parseArpClients: {'3/3' if got == exp else 'FAIL'}")

# ── WidgetStackView paging (wrap index + slide continuity) ────────────────────
def stack_target(index, n, dy):
    return (index + 1) % n if dy < 0 else (index - 1 + n) % n
def incoming_ty(h, dy):
    # incoming card position: from ±h toward 0 as |dy|→h
    return (h + dy) if dy < 0 else (-h + dy)

n_st = 0
H = 150.0
# swipe up from each index advances by +1 (wrap), swipe down retreats by -1 (wrap)
for idx in range(3):
    if stack_target(idx, 3, -10) != (idx + 1) % 3: print(f"STACK up FAIL idx={idx}"); fails += 1; n_st += 1
    if stack_target(idx, 3, +10) != (idx - 1 + 3) % 3: print(f"STACK down FAIL idx={idx}"); fails += 1; n_st += 1
# continuity: at full-drag the incoming card must reach exactly 0 (no seam/jump)
if abs(incoming_ty(H, -H) - 0.0) > 1e-6: print("STACK up continuity FAIL"); fails += 1; n_st += 1
if abs(incoming_ty(H, +H) - 0.0) > 1e-6: print("STACK down continuity FAIL"); fails += 1; n_st += 1
# at rest the incoming card is exactly one frame off-screen (hidden)
if incoming_ty(H, 0.0) not in (H, -H): print("STACK rest-offset FAIL"); fails += 1; n_st += 1
print(f"WidgetStackView paging: {'8/8' if n_st == 0 else 'FAIL'}")

# ── TextReplacementStore.expand / adaptCase ───────────────────────────────────
def adapt_case(typed, phrase):
    if not typed or not phrase: return phrase
    letters = [c for c in typed if c.isalpha()]
    if len(letters) > 1 and all(c.isupper() for c in letters):
        return phrase.upper()
    if typed[0].isupper():
        return phrase[0].upper() + phrase[1:]
    return phrase

def tr_expand(rules, typed):
    for sc, ph in rules:
        if sc.lower() == typed.lower():
            return adapt_case(typed, ph)
    return None

RULES = [("спс", "спасибо"), ("омг", "о, мой бог")]
tr_cases = [
    ("спс", "спасибо"),          # verbatim
    ("Спс", "Спасибо"),          # capitalised shortcut → capitalised phrase
    ("СПС", "СПАСИБО"),          # all-caps → upper phrase
    ("омг", "о, мой бог"),       # multi-word phrase verbatim
    ("Омг", "О, мой бог"),       # capitalise only first char, not each word
    ("xyz", None),               # no match
]
n_tr = 0
for typed, exp in tr_cases:
    if tr_expand(RULES, typed) != exp: print(f"TR FAIL typed={typed} exp={exp} got={tr_expand(RULES, typed)}"); fails += 1; n_tr += 1
print(f"TextReplacement.expand: {len(tr_cases)-n_tr}/{len(tr_cases)}")

# ── WeatherActivity: wmoToCondition / isoTime (open-meteo keyless source) ─────
def wmo_to_condition(code):
    if code == 0: return "clear"
    if code in (1, 2): return "partly-cloudy"
    if code == 3: return "overcast"
    if code in (45, 48): return "fog"
    if code in (51, 53, 55, 56, 57): return "light-rain"
    if code in (61, 63, 66, 67, 80, 81, 82): return "rain"
    if code == 65: return "heavy-rain"
    if code in (71, 77, 85): return "light-snow"
    if code in (73, 75, 86): return "snow"
    if code in (95, 96, 99): return "thunderstorm"
    return "partly-cloudy"

def iso_time(iso):
    return iso[11:16] if len(iso) >= 16 and "T" in iso else iso

# These must map onto the substrings the RU/icon/colour converters already key on.
def icon_code(code):  # mirror of conditionToIconCode (substring checks)
    for k, v in [("clear","sun"),("partly-cloudy","partly-cloudy"),("cloudy","cloud"),
                 ("overcast","cloud"),("rain","rain"),("drizzle","rain"),("thunder","thunder"),
                 ("snow","snow"),("hail","snow"),("fog","fog")]:
        if k in code: return v
    return "partly-cloudy"

n_w = 0
wmo_cases = [
    (0, "clear", "sun"), (2, "partly-cloudy", "partly-cloudy"), (3, "overcast", "cloud"),
    (45, "fog", "fog"), (61, "rain", "rain"), (65, "heavy-rain", "rain"),
    (71, "light-snow", "snow"), (95, "thunderstorm", "thunder"), (999, "partly-cloudy", "partly-cloudy"),
]
for code, cond, icon in wmo_cases:
    got = wmo_to_condition(code)
    if got != cond: print(f"WMO FAIL {code} exp {cond} got {got}"); fails += 1; n_w += 1
    elif icon_code(got) != icon: print(f"WMO-ICON FAIL {code} cond {got} exp {icon} got {icon_code(got)}"); fails += 1; n_w += 1
if iso_time("2026-06-27T05:47") != "05:47": print("ISO FAIL"); fails += 1; n_w += 1
print(f"Weather wmo/iso: {len(wmo_cases)+1-n_w}/{len(wmo_cases)+1}")

# ── RuosAccent.read — opaque clamp + default ──────────────────────────────────
DEFAULT_ACCENT = 0xFF0A84FF
def accent_read(stored):
    # stored is None when unset → DEFAULT; always forced opaque (alpha 0xFF)
    v = DEFAULT_ACCENT if stored is None else stored
    return (v | 0xFF000000) & 0xFFFFFFFF
n_ac = 0
accent_cases = [
    (None, 0xFF0A84FF),          # unset → default blue
    (0xFFD94F3D, 0xFFD94F3D),    # opaque brand red survives
    (0x0000FF00, 0xFF00FF00),    # a stored value with 0 alpha is forced opaque (not transparent)
    (0x00000000, 0xFF000000),    # stored 0 → opaque black, never transparent
]
for stored, exp in accent_cases:
    if accent_read(stored) != exp: print(f"ACCENT FAIL stored={stored} exp={hex(exp)} got={hex(accent_read(stored))}"); fails += 1; n_ac += 1
print(f"RuosAccent.read: {len(accent_cases)-n_ac}/{len(accent_cases)}")

sys.exit(1 if fails else 0)
