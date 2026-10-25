"""Pair raw Berry large-file transfers with the native parser's JSON output from NoiseFit logs."""
import json
import re
import sys
from pathlib import Path

LINE = re.compile(r"^(\S+ \S+) I/noise-fit: Watch nf2 (\w+) ---> (?:<[A-Z]+> )?\s*(.*)$")


def frames(path):
    for raw in Path(path).read_text(errors="replace").splitlines():
        m = LINE.match(raw)
        if m:
            yield m.groups()


def main(log_path, out_dir):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    session, sessions = None, []
    for ts, tag, msg in frames(log_path):
        if tag == "BerryBluetoothService" and msg.startswith("SPP received"):
            data = bytes.fromhex(msg.split(":", 1)[1].replace(" ", ""))
            if data[:4] == b"\x11\x11HZ":
                session = {"ts": ts, "header": data.hex(), "chunks": [], "json": []}
                sessions.append(session)
            elif session is not None and data[1:3] == b"HZ" and data[0] in (0x22, 0x33, 0x44):
                session["chunks"].append(data.hex())
            elif session is not None and session["chunks"] and data[:4] not in (b"\x99\x99HZ", b"\x66\x66HZ", b"\x88\x88HZ"):
                session["chunks"][-1] += data.hex()
        elif tag == "BerryFitnessParsing" and msg.startswith("parsingSingleFitnessData:{") and session is not None:
            try:
                session["json"].append(json.loads(msg.split(":", 1)[1]))
            except json.JSONDecodeError:
                session["json"].append({"_truncated": msg[:200]})
    (out / "sessions.json").write_text(json.dumps(sessions, indent=1))
    print(len(sessions), "sessions;", sum(len(s["json"]) for s in sessions), "decoded records")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
