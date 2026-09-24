import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTERESTS = ROOT / "automation" / "interests.json"
FEEDBACK = ROOT / "automation" / "feedback.json"

DELTA = {
    "keep": 5,
    "engage": 8,
    "reject": -8,
    "ignore": -2
}

def load(path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))

def save(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

def main():
    cfg = load(INTERESTS, {})
    feedback = load(FEEDBACK, [])
    if not feedback:
        print("LEARN_APPLIED=0")
        return

    by_name = {t.get("name"): t for t in cfg.get("topics", [])}
    applied = 0

    for row in feedback:
        if row.get("applied"):
            continue
        topic = row.get("topic")
        action = row.get("action")
        if topic not in by_name or action not in DELTA:
            row["applied"] = True
            row["note"] = "ignored-invalid"
            continue

        item = by_name[topic]
        lo = int(cfg.get("min_weight", 20))
        hi = int(cfg.get("max_weight", 100))
        item["weight"] = max(lo, min(hi, int(item.get("weight", 50)) + DELTA[action]))
        row["applied"] = True
        applied += 1

    save(INTERESTS, cfg)
    save(FEEDBACK, feedback)
    print(f"LEARN_APPLIED={applied}")

if __name__ == "__main__":
    main()
