#!/usr/bin/env python3
"""Apply yarn->mojmap renames to Kotlin sources under given roots."""
import json, os, re, sys
from collections import defaultdict

M = "/tmp/opencode/mappings"
data = json.load(open(f"{M}/rename.json"))
class_map = data["classes"]            # yarn fqcn (slashes) -> moj fqcn (slashes)
member_map = data["members"]           # yarn member -> moj member (unambiguous)

# Build dot-form and slash-form replacement tables, longest first.
dot_pairs, slash_pairs = [], []
for y, m in class_map.items():
    dy, dm = y.replace("/", "."), m.replace("/", ".")
    if dy != dm:
        dot_pairs.append((dy, dm))
        slash_pairs.append((y, m))
dot_pairs.sort(key=lambda x: -len(x[0]))
slash_pairs.sort(key=lambda x: -len(x[0]))

# member regexes, only where rename changes something
member_replacements = {k: v for k, v in member_map.items() if k != v and k.isidentifier() and v.isidentifier()}
kw = set("as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while by get set field it".split())
# Names that collide with Kotlin stdlib extensions / ubiquitous identifiers:
# renaming these via text substitution hits unrelated receivers (str.toInt(),
# list.isNotEmpty(), our own .user/.target properties) and corrupts code.
STOPLIST = set('''
toInt toLong toFloat toDouble toBoolean toString isNotEmpty isEmpty isNotBlank isBlank
first last firstOrNull single orNull takeIf takeUnless let also apply run with plus minus
getOrNull getOrDefault user network target text world camera pos id name value index size
width height count state type key data input output result builder client server player
stack item block entity level chunk message content tag list map set string int long float
double boolean byte short char any unit pair triple range sequence iterator next hasNext
close flush copy clone equals hashCode compare compareTo invoke bind unbind init setup
update render draw tick start stop open closeAll save load reset clear add remove put get
has contains find filter fold forEach associate group partition sorted reversed distinct
'''.split())
member_words = {k: v for k, v in member_replacements.items() if k.isidentifier() and k not in kw and k not in STOPLIST}
# Only rename explicit member accesses (x.name / x?.name); bare words may be
# our own declarations and must stay untouched.
member_rx = re.compile(r"(?<=[.?])(" + "|".join(sorted(member_words, key=len, reverse=True)) + r")(?![\w])")
print(f"{len(dot_pairs)} class pairs, {len(member_words)} member words", file=sys.stderr)

# Global protection: identifiers our own code declares as enum entries or
# const values must never be renamed, even via dot-access in other files.
GLOBAL_PROTECTED = set()

import_rx = re.compile(r"^(\s*import\s+)([\w.$]+)(\s*)$")
ambiguous_log = defaultdict(int)

def map_import(fqcn_dots):
    """Longest-prefix class_map match on dotted fqcn; returns moj fqcn or None."""
    cand = fqcn_dots.replace(".", "/")
    # exact or ancestor-with-$ or inner-by-dot
    best = None
    for sl, ms in slash_pairs:   # sorted longest first
        if cand == sl or cand.startswith(sl + "$") or cand.startswith(sl + "."):
            rest = cand[len(sl):]
            return (ms + rest).replace("/", ".")
    return None

def transform(path):
    src = open(path).read()
    orig = src
    out_lines = []
    file_simple = {}   # yarn simple -> moj simple (unique within file)
    conflicts = set()
    for line in src.splitlines(keepends=True):
        m = import_rx.match(line.rstrip("\n"))
        if m:
            fq = m.group(2)
            mapped = map_import(fq)
            if mapped:
                line = f"{m.group(1)}{mapped}{m.group(3)}\n"
                ys, ms = fq.rsplit(".", 1)[-1], mapped.rsplit(".", 1)[-1]
                if ys != ms:
                    if ys in file_simple and file_simple[ys] != ms:
                        conflicts.add(ys)
                    else:
                        file_simple[ys] = ms
        out_lines.append(line)
    src = "".join(out_lines)

    # contextual: yarn .world on Minecraft-like receivers is field 'level';
    # on entity-ish receivers it is method level()
    pass  # contextual world rules disabled pending per-receiver validation

    # qualified refs in code (dot form then slash form)
    for y, m in dot_pairs:
        src = src.replace(y, m)
    for y, m in slash_pairs:
        src = src.replace(y, m)

    # imported simple names
    for ys, ms in file_simple.items():
        if ys in conflicts:
            continue
        src = re.sub(r"(?<![\w.])" + re.escape(ys) + r"(?![\w])", ms, src)

    # members; definition sites protected via callback check
    active_words = member_words
    def_word = re.compile(r"\b(?:val|var|fun)\s+(\w+)")
    defs_here = {m.group(1) for m in def_word.finditer(src)}
    # also treat constructor/val params as definitions
    for pm in re.finditer(r"\(([^)]*)\)", src):
        for seg in pm.group(1).split(","):
            seg = seg.strip()
            if ":" in seg:
                pn = seg.split(":")[0].strip()
                if pn.isidentifier():
                    defs_here.add(pn)
    active_words = {k: v for k, v in active_words.items() if k not in defs_here}
    rx = re.compile(r"(?<=[.?])(" + "|".join(sorted(active_words, key=len, reverse=True)) + r")(?![\w])")
    src = rx.sub(lambda mo: active_words[mo.group(1)], src)

    # implicit-receiver calls (Kotlin: addDrawableChild(...) inside Screen subclass).
    bare_words = active_words
    brx = re.compile(r"""(?<!fun )(?<![\w.?"'])(get[A-Z]\w*|is[A-Z]\w*|set[A-Z]\w*|[a-z][a-z0-9]*(?:[A-Z][a-zA-Z0-9]+)+)(\s*\()""")
    def sub_bare(mo):
        rep = bare_words.get(mo.group(1))
        return (rep + mo.group(2)) if rep else mo.group(0)
    src = brx.sub(sub_bare, src)

    if src != orig:
        open(path, "w").write(src)
        return True
    return False

# scan roots once for protected names
enum_rx = re.compile(r"^\s*([A-Z][A-Z0-9_]{1,})\s*(?:[(;,]|$)", re.M)
const_rx = re.compile(r"\b(?:const val|val|var)\s+([A-Z][A-Z0-9_]{1,})\b")
for root in sys.argv[1:]:
    for dp, _, fs in os.walk(root):
        for fn in fs:
            if fn.endswith(".kt"):
                t = open(os.path.join(dp, fn)).read()
                GLOBAL_PROTECTED.update(enum_rx.findall(t))
                GLOBAL_PROTECTED.update(const_rx.findall(t))
member_words = {k: v for k, v in member_words.items() if k not in GLOBAL_PROTECTED}
print(f"global protected names: {len(GLOBAL_PROTECTED)}", file=sys.stderr)

changed = 0
for root in sys.argv[1:]:
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(".kt"):
                if transform(os.path.join(dirpath, fn)):
                    changed += 1
print(f"files changed: {changed}", file=sys.stderr)
