import json, os, hashlib, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
QUEUE = ROOT / "automation" / "queue.json"
POSTED = ROOT / "automation" / "posted.json"
DRY_RUN = os.getenv("DRY_RUN", "true").lower() != "false"

REVIEW_TOPICS = {"politics", "breaking-news"}
MIN_CONFIDENCE = 85
NOISE_TERMS = {"friendly","friendlies","football","soccer","head-to-head","h2h","livestream","live stream","watch live","tv channel","gossip"}

def load(path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))

def fingerprint(text):
    return hashlib.sha256(" ".join(text.lower().split()).encode()).hexdigest()[:20]

def eligible(item, seen):
    text = item.get("text","").strip()
    if not text or len(text) > 280:
        return False, "invalid-length"
    lowered = text.lower()
    if any(term in lowered for term in NOISE_TERMS):
        return False, "noise-filter"
    if item.get("topic") in REVIEW_TOPICS:
        return False, "manual-review-topic"
    if item.get("status") != "auto":
        return False, "not-approved"
    if int(item.get("confidence",0)) < MIN_CONFIDENCE:
        return False, "low-confidence"
    if fingerprint(text) in seen:
        return False, "duplicate"
    return True, "ok"

def publish_to_x(text):
    if DRY_RUN:
        print("DRY_RUN POST:", text)
        return {"ok": True, "dry_run": True, "id": "dry-run"}
    from requests_oauthlib import OAuth1Session
    required = ["X_API_KEY","X_API_SECRET","X_ACCESS_TOKEN","X_ACCESS_TOKEN_SECRET"]
    missing = [k for k in required if not os.getenv(k)]
    if missing:
        raise RuntimeError("Missing X secrets: " + ", ".join(missing))
    oauth = OAuth1Session(
        os.environ["X_API_KEY"],
        client_secret=os.environ["X_API_SECRET"],
        resource_owner_key=os.environ["X_ACCESS_TOKEN"],
        resource_owner_secret=os.environ["X_ACCESS_TOKEN_SECRET"],
    )
    r = oauth.post("https://api.x.com/2/tweets", json={"text": text}, timeout=30)
    r.raise_for_status()
    return r.json()

def main():
    queue = load(QUEUE, [])
    posted = load(POSTED, [])
    seen = {x["fingerprint"] for x in posted if "fingerprint" in x}

    chosen = None
    for item in queue:
        ok, reason = eligible(item, seen)
        print(item.get("id"), reason)
        if ok:
            chosen = item
            break

    if not chosen:
        print("No eligible post.")
        return

    result = publish_to_x(chosen["text"])
    if not result.get("ok", True):
        raise RuntimeError(str(result))

    if DRY_RUN:
        return

    posted.append({
        "id": chosen["id"],
        "fingerprint": fingerprint(chosen["text"]),
        "posted_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "x_result": result
    })
    POSTED.write_text(json.dumps(posted, indent=2, ensure_ascii=False), encoding="utf-8")
    queue = [x for x in queue if x.get("id") != chosen.get("id")]
    QUEUE.write_text(json.dumps(queue, indent=2, ensure_ascii=False), encoding="utf-8")

if __name__ == "__main__":
    main()
