#!/usr/bin/env python3
"""
Static pre-build gate — catches the errors a first `soong`/`aapt2` run would catch, without
an AOSP tree. Run this BEFORE a device build to maximise the chance of a clean first compile:

  * every AndroidManifest.xml and res/**/*.xml is well-formed (aapt2 hard-fails otherwise)
  * every Kotlin file has balanced () {} [] (a truncated edit = compile error) — lexer-based,
    so strings / chars / comments / triple-quotes / ${templates} don't cause false positives
  * every app-local R.<type>.<name> referenced in code actually exists in that app's res/
  * every Android.bp is brace-balanced and names a module
  * no two source files in one module declare the same top-level class/object (FQN clash)

Non-zero exit on any problem (CI-friendly).
Run:  python3 vendor/ruos/tools/verify/verify_build.py
"""
import os, re, sys, glob, xml.etree.ElementTree as ET

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "../../../.."))
APPS = os.path.join(ROOT, "packages/apps")
fails = []
def fail(msg): fails.append(msg)

# ── 1. XML well-formedness ────────────────────────────────────────────────────
xml_count = 0
for x in glob.glob(os.path.join(APPS, "**/*.xml"), recursive=True) + \
         glob.glob(os.path.join(ROOT, "vendor/ruos/overlay/**/*.xml"), recursive=True):
    xml_count += 1
    try: ET.parse(x)
    except ET.ParseError as e: fail(f"XML malformed: {os.path.relpath(x, ROOT)}: {e}")

# ── 2. Kotlin bracket balance (lexer-aware) ───────────────────────────────────
def strip_kotlin(src):
    """Remove comments / string / char literals so only structural brackets remain."""
    out = []; i = 0; n = len(src)
    while i < n:
        c = src[i]; two = src[i:i+2]
        if two == "//":
            i = src.find("\n", i);
            if i < 0: break
            continue
        if two == "/*":
            j = src.find("*/", i+2); i = (j+2) if j >= 0 else n; continue
        if src[i:i+3] == '"""':
            j = src.find('"""', i+3); i = (j+3) if j >= 0 else n; continue
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == '\\': i += 2
                else: i += 1
            i += 1; continue
        if c == "'":
            i += 1
            while i < n and src[i] != "'":
                if src[i] == '\\': i += 2
                else: i += 1
            i += 1; continue
        out.append(c); i += 1
    return "".join(out)

PAIRS = {')': '(', ']': '[', '}': '{'}
OPEN = set("([{")
kt_files = glob.glob(os.path.join(APPS, "**/*.kt"), recursive=True) + \
           glob.glob(os.path.join(ROOT, "frameworks/base/packages/SystemUI/ruos-src/**/*.kt"), recursive=True)
for k in kt_files:
    src = strip_kotlin(open(k, encoding="utf-8", errors="replace").read())
    stack = []
    ok = True
    for ch in src:
        if ch in OPEN: stack.append(ch)
        elif ch in PAIRS:
            if not stack or stack[-1] != PAIRS[ch]: ok = False; break
            stack.pop()
    if not ok or stack:
        fail(f"Kotlin bracket imbalance: {os.path.relpath(k, ROOT)} (depth {len(stack)})")

# ── 3. app-local R.<type>.<name> references resolve ───────────────────────────
RES_RE = re.compile(r'\bR\.(string|drawable|mipmap|raw|xml|layout|array|color|id|style|dimen|anim|font|bool|integer|menu|plurals)\.(\w+)')
for appdir in sorted(glob.glob(os.path.join(APPS, "*"))):
    res = os.path.join(appdir, "res")
    if not os.path.isdir(res): continue
    # gather declared resource names
    declared = set()
    for vx in glob.glob(os.path.join(res, "values*", "*.xml")):
        try:
            for el in ET.parse(vx).getroot():
                nm = el.get("name")
                if nm: declared.add(nm)
        except ET.ParseError: pass
    for sub in ("drawable","mipmap","raw","xml","layout","anim","font","menu"):
        for f in glob.glob(os.path.join(res, sub + "*", "*")):
            declared.add(os.path.splitext(os.path.basename(f))[0])
    # ids are also declared inline via @+id/NAME inside layout/menu/xml resources
    for rx in glob.glob(os.path.join(res, "**", "*.xml"), recursive=True):
        for nm in re.findall(r'@\+id/(\w+)', open(rx, encoding="utf-8", errors="replace").read()):
            declared.add(nm)
    for k in glob.glob(os.path.join(appdir, "src/**/*.kt"), recursive=True):
        body = open(k, encoding="utf-8", errors="replace").read()
        # skip android.R.* and other.pkg.R.* — only bare R.type.name binds to this module
        for m in RES_RE.finditer(body):
            # ignore if immediately preceded by '.' (e.g. android.R.) — handled by \b? add guard
            start = m.start()
            if start >= 1 and body[start-1] == '.': continue
            rtype, rname = m.group(1), m.group(2)
            if rname not in declared:
                fail(f"R.{rtype}.{rname} missing in {os.path.basename(appdir)}/res "
                     f"({os.path.relpath(k, ROOT)})")

# ── 3b. unresolved Capitalised type references (missing imports) ──────────────
# Catches the #1 first-compile failure a kotlinc would catch. Harvests every local
# binding (classes, enum entries, const/val/fun names, AIDL interfaces) so it does not
# false-positive on enum constants or local helpers.
KOTLIN_BUILTINS = set("""Int Long Short Byte Double Float Boolean Char String CharSequence Any Unit
 Nothing List MutableList ArrayList Map MutableMap HashMap LinkedHashMap Set MutableSet HashSet
 LinkedHashSet Pair Triple Array IntArray FloatArray DoubleArray LongArray ByteArray BooleanArray
 CharArray Throwable Exception RuntimeException Error IllegalStateException IllegalArgumentException
 IndexOutOfBoundsException NumberFormatException SecurityException UnsupportedOperationException
 Iterable Sequence Comparable Comparator Runnable Thread Math System Object StringBuilder Number
 Regex Result Lazy Enum Collection Class Override Deprecated JvmStatic JvmField JvmOverloads
 Volatile Synchronized Suppress LayoutParams Companion Stub Builder""".split())

def strip_comments_strings(body):
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    body = re.sub(r'//[^\n]*', '', body)
    body = re.sub(r'""".*?"""', '""', body, flags=re.S)
    body = re.sub(r'"(\\.|[^"\\\n])*"', '""', body)
    return body

def simple_import(imp): return imp.strip().split(" as ")[-1].split(".")[-1]

# map package -> set of type-ish names declared anywhere in that package (cross-file)
pkg_types = {}
kt_by_pkg = {}
for k in kt_files:
    body = open(k, encoding="utf-8", errors="replace").read()
    pm = re.search(r'^\s*package\s+([\w.]+)', body, re.M)
    if not pm: continue
    pkg = pm.group(1); kt_by_pkg.setdefault(pkg, []).append(k)
    names = set(re.findall(r'(?:class|object|interface)\s+(\w+)', body))
    # enum entries:  enum class X { A, B(...), C ; ... }
    for em in re.finditer(r'enum\s+class\s+\w+[^{]*\{(.*?)(?:;|\})', body, re.S):
        for ent in re.findall(r'\b([A-Z][A-Z0-9_]*|[A-Z]\w*)\s*[,(\n]', em.group(1)):
            names.add(ent)
    # top-level & companion consts/vals/vars and Capitalised funs
    names |= set(re.findall(r'(?:const\s+)?va[lr]\s+([A-Z]\w+)', body))
    names |= set(re.findall(r'\bfun\s+([A-Z]\w+)\s*\(', body))
    pkg_types.setdefault(pkg, set()).update(names)
# AIDL-generated interface names per dir
aidl_by_dir = {}
for a in glob.glob(os.path.join(APPS, "**/*.aidl"), recursive=True):
    aidl_by_dir.setdefault(os.path.dirname(a), set()).add(os.path.splitext(os.path.basename(a))[0])

import_fails = 0
for k in kt_files:
    body = open(k, encoding="utf-8", errors="replace").read()
    pm = re.search(r'^\s*package\s+([\w.]+)', body, re.M)
    if not pm: continue
    pkg = pm.group(1)
    if re.search(r'^import\s+[\w.]+\.\*', body, re.M): continue   # wildcard import: can't resolve
    code = strip_comments_strings(body)
    imports = {simple_import(m) for m in re.findall(r'^import\s+([\w.][\w. ]+?)\s*$', body, re.M)}
    local = set(pkg_types.get(pkg, set())) | imports | KOTLIN_BUILTINS
    local |= aidl_by_dir.get(os.path.dirname(k), set())
    refs = set()
    for m in re.finditer(r'(?<![\w.])([A-Z]\w+)\s*\(', code): refs.add(m.group(1))
    for m in re.finditer(r':\s*([A-Z]\w+)', code): refs.add(m.group(1))
    for m in re.finditer(r'(?<![\w.])(?:is|as)\s+([A-Z]\w+)', code): refs.add(m.group(1))
    for r in sorted(refs):
        if r in local: continue
        if re.search(r'\.' + re.escape(r) + r'\b', code): continue   # used fully-qualified somewhere
        fail(f"unresolved type '{r}' (missing import?) in {os.path.relpath(k, ROOT)}")
        import_fails += 1

# ── 4. Android.bp structural sanity ───────────────────────────────────────────
for bp in glob.glob(os.path.join(APPS, "*/Android.bp")):
    txt = open(bp).read()
    if txt.count("{") != txt.count("}"):
        fail(f"Android.bp brace imbalance: {os.path.relpath(bp, ROOT)}")
    if "name:" not in txt:
        fail(f"Android.bp has no module name: {os.path.relpath(bp, ROOT)}")

# ── 5. duplicate top-level class/object FQN within a module ───────────────────
for appdir in sorted(glob.glob(os.path.join(APPS, "*"))):
    seen = {}
    for k in glob.glob(os.path.join(appdir, "src/**/*.kt"), recursive=True):
        body = open(k, encoding="utf-8", errors="replace").read()
        p = re.search(r'^\s*package\s+([\w.]+)', body, re.M)
        pkg = p.group(1) if p else ""
        for m in re.finditer(r'^(?:public |internal |private |abstract |open |sealed |data |enum )*(?:class|object|interface)\s+(\w+)', body, re.M):
            fqn = pkg + "." + m.group(1)
            if fqn in seen and seen[fqn] != k:
                fail(f"duplicate type {fqn}: {os.path.relpath(seen[fqn], ROOT)} & {os.path.relpath(k, ROOT)}")
            seen[fqn] = k

print(f"checked: {xml_count} XML, {len(kt_files)} Kotlin files")
if fails:
    print(f"\n{len(fails)} PROBLEM(S):")
    for f in fails: print("  FAIL:", f)
    sys.exit(1)
print("ALL STATIC BUILD CHECKS PASSED")
