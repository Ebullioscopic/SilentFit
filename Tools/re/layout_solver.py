"""Locate JSON fields from the native parser inside raw record payloads (known-plaintext layout recovery)."""
import json
import struct
import sys
from collections import defaultdict

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from decode_fitness import HDR, reassemble, records  # noqa: E402


def find_seq(buf, values, width):
    fmt = {1: "B", 2: "H", 4: "I"}[width]
    pat = struct.pack(f"<{len(values)}{fmt}", *values)
    hits, start = [], 0
    while (i := buf.find(pat, start)) != -1:
        hits.append(i); start = i + 1
    return hits


def main(path):
    sessions = json.load(open(path))
    layout = defaultdict(lambda: defaultdict(set))
    for s in sessions:
        data, recs = records(reassemble(s))
        decoded = [j for j in s["json"] if "type_id" in j]
        for off, ts, t, ver in recs:
            end = next((r[0] for r in recs if r[0] > off), len(data))
            payload = data[off + HDR:end]
            match = [j for j in decoded if j["type_id"]["fitness_function_type"] == t
                     and (j["type_id"]["year"], j["type_id"]["month"], j["type_id"]["day"]) == ts[:3]]
            if not match:
                continue
            j = match[0]
            for k, v in j.items():
                if k == "type_id" or isinstance(v, (dict, str)):
                    continue
                vals = v if isinstance(v, list) else [v]
                if not vals or all(x == 0 for x in vals) or any((not isinstance(x, int)) or x < 0 for x in vals):
                    continue
                for w in (1, 2, 4):
                    if max(vals) >= 1 << (8 * w):
                        continue
                    hits = find_seq(payload, vals, w)
                    if hits:
                        layout[t][k].add((w, tuple(hits[:3]), len(vals), len(payload)))
                        break
    for t in sorted(layout):
        print(f"== type {t}")
        for k, v in sorted(layout[t].items(), key=lambda kv: min(h[1][0] for h in kv[1])):
            print(f"  {k}: {sorted(v)}")


if __name__ == "__main__":
    main(sys.argv[1])
