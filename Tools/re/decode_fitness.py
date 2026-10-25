"""Prototype decoder for Berry daily fitness large files; validated against native-parser JSON in logs."""
import json
import struct
import sys

HDR = 9  # year(u16) month day hour minute second fitness_type data_struct_ver


def reassemble(session):
    head = bytes.fromhex(session["header"])
    body = head[9:]  # 11 11 48 5A | serial | event | type | len(u16)
    for c in session["chunks"]:
        b = bytes.fromhex(c)
        body += b[6:]  # 22 48 5A | len(u16) | index
    return body


def records(body):
    total = struct.unpack_from("<I", body, 0)[0]
    data = body[12:12 + total]
    out, i = [], 0
    while i + HDR <= len(data):
        y, mo, d, h, mi, s, t, ver = struct.unpack_from("<HBBBBBBB", data, i)
        if not (2000 < y < 2100):
            break
        out.append((i, (y, mo, d, h, mi, s), t, ver))
        i += 1
        # next record: search the next plausible header
        nxt = None
        for j in range(i, len(data) - HDR):
            yy, mm, dd = struct.unpack_from("<HBB", data, j)
            if yy == y and 1 <= mm <= 12 and 1 <= dd <= 31 and data[j + 7] <= 24 and data[j + 8] in (1, 2, 3):
                nxt = j
                break
        if nxt is None:
            break
        i = nxt
    return data, out


def u16s(b, off, n):
    return list(struct.unpack_from(f"<{n}H", b, off))


if __name__ == "__main__":
    sessions = json.load(open(sys.argv[1]))
    s = sessions[int(sys.argv[2])]
    body = reassemble(s)
    data, recs = records(body)
    print("file total", struct.unpack_from("<I", body, 0)[0], "prefix", body[:12].hex(" "))
    for k, (off, ts, t, ver) in enumerate(recs):
        end = recs[k + 1][0] if k + 1 < len(recs) else len(data)
        print(f"@{off} len={end - off - HDR} ts={ts} type={t} ver={ver}")
        print("   ", data[off + HDR:end].hex(" ")[:600])
