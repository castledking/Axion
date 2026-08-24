#!/usr/bin/env python3
"""Build yarn->mojmap rename tables. All descriptors normalized to OFFICIAL namespace."""
import json, re, sys
from collections import defaultdict

M = "/tmp/opencode/mappings"

def parse_tiny(path):
    classes, mrows, frows = {}, [], []
    cur_a = None
    with open(path) as f:
        for line in f:
            p = [x for x in line.rstrip("\n").split("\t") if x != ""]
            if len(p) < 2:
                continue
            if p[0] == "c" and len(p) >= 3:
                cur_a = p[1]
                classes[p[1]] = p[2]
            elif p[0] == "m" and len(p) >= 4 and cur_a:
                mrows.append((cur_a, p[2], p[1], p[3]))
            elif p[0] == "f" and len(p) >= 4 and cur_a:
                frows.append((cur_a, p[2], p[1], p[3]))
    return classes, mrows, frows

int_cls, int_m, _ = parse_tiny(f"{M}/inter_/mappings/mappings.tiny")   # official -> intermediary
y_cls, y_mrows, y_frows = parse_tiny(f"{M}/mappings/mappings.tiny")    # intermediary -> yarn

moj_class = {}
moj_members = defaultdict(list)
cur_off = None
with open(f"{M}/mojmap.txt") as f:
    for line in f:
        if line.startswith("#") or not line.strip():
            continue
        m = re.match(r"^(\S+) -> (\S+):$", line)
        if m:
            cur_off = m.group(2); moj_class[cur_off] = m.group(1); continue
        line_s = line.rstrip()
        t = re.search(r" -> (\S+)$", line_s)
        if not t or not cur_off:
            continue
        off_name = t.group(1)
        lhs = re.sub(r"^\d+:\d+:", "", line_s[:t.start()].strip()).strip()
        if "(" in lhs:
            name = lhs[:lhs.index("(")].split()[-1] or "<init>"
            moj_members[(cur_off, "m", off_name)].append((lhs[lhs.index("("):].replace(" ", ""), name))
        else:
            moj_members[(cur_off, "f", off_name)].append((None, lhs.split()[-1]))

moj_to_off = {v.replace('.', '/'): k for k, v in moj_class.items()}
int_to_off = {i: o for o, i in int_cls.items()}
# extend maps with inner-class forms
def expand(d):
    out = dict(d)
    for k, v in d.items():
        if "$" in k:
            top = k.split("$")[0]
            if top in d:
                out.setdefault(k, v)
    return out
int_to_off_e = expand(int_to_off)
moj_to_off_e = expand(moj_to_off)

def remap_desc(desc, ns_map):
    """Remap L-type names inside an internal-format descriptor using ns_map."""
    def sub(mt):
        n = mt.group(1)
        r = ns_map.get(n)
        return "L" + (r if r else n) + ";"
    return re.sub(r"L([^;]+);", sub, desc)

def norm_moj_desc(pd):
    """Mojmap txt method signature '(Type,Type)Ret' -> official-namespace internal form."""
    def conv(t):
        t = t.strip()
        arr = ""
        while t.endswith("[]"):
            arr += "["; t = t[:-2]
        base = "L" + moj_to_off_e.get(t.replace(".", "/"), t.replace(".", "/")) + ";"
        return arr + base
    i = pd.index("(")
    j = pd.rindex(")")
    params = pd[i+1:j]
    ret = pd[j+1:]
    inner = ",".join(conv(p) for p in params.split(",") if p.strip())
    return "(" + inner + ")" + (conv(ret) if ret else "V")

def build_members(rows, kind, desc_ns):
    """Yield (ownerA, nameA, desc_official_or_None, nameB)."""
    if kind == "m":
        for owner, na, desc, nb in rows:
            yield owner, na, desc, nb
    else:
        for owner, na, desc, nb in rows:
            yield owner, na, None, nb

# --- method join ---
# inter rows bridge: (owner_intermediary, member_intermediary) -> official owner/name/desc
_, _, int_frows = parse_tiny(f"{M}/inter_/mappings/mappings.tiny")
bridge = {}
for owner_off, off_name, desc_off, int_name in int_m:
    owner_int = int_cls.get(owner_off)
    if owner_int:
        bridge[(owner_int, int_name)] = (owner_off, off_name)

mmap = defaultdict(set)
for owner_int, int_name, desc_yarn, yarn in y_mrows:
    b = bridge.get((owner_int, int_name))
    if not b:
        continue
    owner_off, off_name = b
    want = remap_desc(desc_yarn, int_to_off_e)   # normalize yarn(intermediary-typed) desc -> official
    cands = moj_members.get((owner_off, "m", off_name))
    if not cands:
        continue
    def norm_full(pd):
        try:
            return norm_moj_desc(pd)
        except Exception:
            return None
    matched = None
    for pdesc, mname in cands:
        nd = norm_full(pdesc) if pdesc is not None else None
        if nd is not None and nd == want:
            matched = mname
            break
    if matched is None:
        # fall back to parameter-only comparison (return types diverge across mappings)
        wp = want[:want.index(")")] if "(" in want else want
        pmatches = []
        for pdesc, mname in cands:
            nd = norm_full(pdesc) if pdesc is not None else None
            if nd is not None and nd[:nd.index(")")] == wp:
                pmatches.append(mname)
        if len(set(pmatches)) == 1:
            matched = pmatches[0]
    if matched is None and len(cands) == 1:
        nd = norm_full(cands[0][0])
        if nd is not None and nd[:max(nd.index(")"),0)] == want[:max(want.index(")"),0)]:
            matched = cands[0][1]
    if matched:
        mmap[yarn].add(matched)

# --- field join ---
y_f_by_key = {}
for owner_int, int_name, _d, yarn in y_frows:
    y_f_by_key[(owner_int, int_name)] = yarn
fbridge = {}
for owner_off, off_name, _d, int_name in int_frows:
    owner_int = int_cls.get(owner_off)
    if owner_int:
        fbridge[(owner_int, int_name)] = (owner_off, off_name)
for key, yarn in y_f_by_key.items():
    b = fbridge.get(key)
    if not b:
        continue
    cands = moj_members.get((b[0], "f", b[1]))
    if not cands:
        continue
    vals = [n for _, n in cands]
    if len(set(vals)) == 1:
        mmap[yarn].add(vals[0])

unambiguous = {y: next(iter(v)) for y, v in mmap.items() if len(v) == 1}
ambiguous = {y: sorted(v) for y, v in mmap.items() if len(v) > 1}

class_map = {}
for i, yarn in y_cls.items():
    off = int_to_off.get(i)
    if off and off in moj_class:
        class_map[yarn] = moj_class[off]

print(f"class renames: {len(class_map)}", file=sys.stderr)
print(f"members: total={len(mmap)} unambiguous={len(unambiguous)} ambiguous={len(ambiguous)}", file=sys.stderr)
json.dump({"classes": class_map, "members": unambiguous, "ambiguous": ambiguous},
          open(f"{M}/rename.json", "w"))
