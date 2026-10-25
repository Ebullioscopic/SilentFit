"""Export real captured fitness syncs (id list frame + raw stream + native-parser JSON) for the env-gated Swift test."""
import json
import re
import sys
from pathlib import Path

LINE = re.compile(r"^(\S+ \S+) I/noise-fit: Watch nf2 (\w+) ---> (?:<[A-Z]+> )?\s*(.*)$")


def main(log_path, out_path):
    lines = Path(log_path).read_text(errors="replace").splitlines()
    sessions, idlist, cur = [], None, None
    for raw in lines:
        m = LINE.match(raw)
        if not m:
            continue
        _, tag, msg = m.groups()
        if tag == "BerryBluetoothService" and msg.startswith("SPP received"):
            data = bytes.fromhex(msg.split(":", 1)[1].replace(" ", ""))
            if data[:4] == b"\x66\x66HZ" and b"\x08\x0b\x10\x8a\x01" in data[9:14] and len(data) > 30:
                idlist = data.hex()
            elif data[:4] == b"\x11\x11HZ":
                cur = {"idlist": idlist, "stream": data.hex(), "expected": []}
                sessions.append(cur)
            elif cur is not None and data[:4] not in (b"\x99\x99HZ", b"\x66\x66HZ", b"\x88\x88HZ"):
                cur["stream"] += data.hex()
            elif data[:4] in (b"\x66\x66HZ", b"\x88\x88HZ"):
                if cur is not None and not cur["expected"]:
                    pass
        elif tag == "BerryFitnessParsing" and cur is not None:
            if msg.startswith("parsingSingleFitnessData:{"):
                try:
                    j = json.loads(msg.split(":", 1)[1])
                except json.JSONDecodeError:
                    continue
                t = j.get("type_id", {})
                exp = {"type": t.get("fitness_function_type"), "day": t.get("day"), "month": t.get("month")}
                if "history_hours_step" in j:
                    exp["hourlySteps"] = j["history_hours_step"]
                    exp["steps"] = j["history_step_sum"]
                if "history_sleep" in j:
                    exp["sleepTotal"] = j["history_sleep"].get("sleep_duration")
                    exp["deep"] = j["history_sleep"].get("deep_sleep_time")
                if "history_effective_standing" in j:
                    exp["standing"] = j["history_effective_standing"]
                cur["expected"].append(exp)
    sessions = [s for s in sessions if s["idlist"] and s["expected"]]
    Path(out_path).write_text(json.dumps(sessions))
    print(len(sessions), "sessions exported")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
