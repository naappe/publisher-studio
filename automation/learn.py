import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTERESTS = ROOT / "automation" / "interests.json"
FEEDBACK = ROOT / "automation" / "feedback.json"

DELTA = {"keep": 5, "engage": 8, "reject": -8, "ignore": -2}

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

    topics = cfg.get("topics", [])
    by_name = {str(t.get("name","")).lower(): t for t in topics}
    by_category = {}
    for t in topics:
        by_category.setdefault(str(t.get("category","")).lower(), t)

    applied = 0
    for row in feedback:
        if row.get("applied"):
            continue

        action = str(row.get("action","")).lower()
        interest = str(row.get("interest") or row.get("topic") or "").lower()
        category = str(row.get("category") or "").lower()
        item = by_name.get(interest) or by_category.get(category) or by_category.get(interest)

        if not item or action not in DELTA:
            row["applied"] = True
            row["note"] = "ignored-invalid"
            continue

        lo = int(cfg.get("min_weight", 20))
        hi = int(cfg.get("max_weight", 100))
        item["weight"] = max(lo, min(hi, int(item.get("weight", 50)) + DELTA[action]))
        row["applied"] = True
        row["applied_to"] = item.get("name")
        applied += 1

    save(INTERESTS, cfg)
    save(FEEDBACK, feedback)
    print(f"LEARN_APPLIED={applied}")

if __name__ == "__main__":
    main()
