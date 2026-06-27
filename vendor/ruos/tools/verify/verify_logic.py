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

sys.exit(1 if fails else 0)
