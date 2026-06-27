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

sys.exit(1 if fails else 0)
