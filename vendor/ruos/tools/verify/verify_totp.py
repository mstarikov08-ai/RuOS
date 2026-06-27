#!/usr/bin/env python3
"""
Correctness check for the RuOS TOTP generator (RuOSKeychain/.../totp/Totp.kt).

These functions are a faithful port of the Kotlin; they assert it against the official
RFC 6238 Appendix B test vectors and RFC 4648 base32. If the Kotlin algorithm ever drifts,
update the port here and this will catch it. (Security-critical: wrong codes = useless 2FA.)

Run:  python3 vendor/ruos/tools/verify/verify_totp.py
"""
import hmac, hashlib, base64, sys

def base32_decode(s):                                    # port of Totp.base32Decode
    alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    clean = s.strip().replace(" ", "").replace("-", "").upper().rstrip("=")
    if clean == "": return b""
    buffer = bits = 0; out = bytearray()
    for c in clean:
        idx = alphabet.find(c)
        if idx < 0: return None
        buffer = (buffer << 5) | idx; bits += 5
        if bits >= 8: bits -= 8; out.append((buffer >> bits) & 0xFF)
    return bytes(out)

def totp_code(secret, time_sec, digits=6, period=30):    # port of Totp.code
    key = base32_decode(secret)
    if key is None or len(key) == 0: return "-" * digits
    counter = time_sec // period
    msg = bytearray(8); v = counter
    for i in range(7, -1, -1): msg[i] = v & 0xFF; v >>= 8
    h = hmac.new(key, bytes(msg), hashlib.sha1).digest()
    offset = h[-1] & 0x0F
    binary = ((h[offset] & 0x7F) << 24) | ((h[offset+1] & 0xFF) << 16) | \
             ((h[offset+2] & 0xFF) << 8) | (h[offset+3] & 0xFF)
    return str(binary % (10 ** digits)).rjust(digits, "0")

def main():
    fails = 0
    seed = base64.b32encode(b"12345678901234567890").decode()      # RFC 6238 SHA1 seed
    rfc = [(59, "94287082"), (1111111109, "07081804"), (1111111111, "14050471"),
           (1234567890, "89005924"), (2000000000, "69279037"), (20000000000, "65353130")]
    for t, exp in rfc:
        got = totp_code(seed, t, digits=8)
        if got != exp: print(f"RFC6238 FAIL t={t} exp={exp} got={got}"); fails += 1
    print(f"RFC 6238 vectors: {len(rfc)-fails}/{len(rfc)}")

    b = 0
    for data in [b"", b"f", b"fo", b"foo", b"foob", b"fooba", b"foobar", b"RuOS-2FA!"]:
        if base32_decode(base64.b32encode(data).decode()) != data: print("base32 FAIL", data); fails += 1; b += 1
    print(f"RFC 4648 base32 round-trip: ok ({'no' if b==0 else b} failures)")

    sys.exit(1 if fails else 0)

if __name__ == "__main__":
    main()
