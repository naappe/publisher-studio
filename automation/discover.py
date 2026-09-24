import json, hashlib, html, re, urllib.parse, xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime, timezone, timedelta
from email.utils import parsedate_to_datetime
from difflib import SequenceMatcher
import requests

ROOT = Path(__file__).resolve().parents[1]
QUEUE = ROOT / "automation" / "queue.json"
INTERESTS = ROOT / "automation" / "interests.json"

SENSITIVE_TERMS = {
    "president","minister","ministry","government","parliament","election","candidate",
    "party","ambassador","foreign affairs","police","court","protest","military"
}

NOISE_TERMS = {
    "friendly","friendlies","match","fixture","football","soccer","cricket","tennis",
    "basketball","score","head-to-head","h2h","movie","celebrity","gossip"
}

def load(path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))

def save(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

def norm(s):
    return " ".join((s or "").lower().split())

def fid(text):
    return hashlib.sha256(norm(text).encode()).hexdigest()[:16]

def clean_title(title):
    title = html.unescape(title or "")
    title = re.sub(r"\s+-\s+[^-]{2,80}$", "", title).strip()
    return " ".join(title.split())

def google_news_rss(query):
    q = urllib.parse.quote(query)
    return f"https://news.google.com/rss/search?q={q}&hl=en&gl=US&ceid=US:en"

def fetch_items(query):
    url = google_news_rss(query)
    r = requests.get(url, timeout=20, headers={"User-Agent":"PublisherStudioAutopilot/1.2"})
    r.raise_for_status()
    root = ET.fromstring(r.text)
    out = []
    for item in root.findall(".//item"):
        title = clean_title(item.findtext("title"))
        link = (item.findtext("link") or "").strip()
        pub = (item.findtext("pubDate") or "").strip()
        source = item.find("source")
        source_name = (source.text or "").strip() if source is not None else ""
        source_url = source.attrib.get("url","").strip() if source is not None else ""
        if title and link:
            out.append({"title": title, "link": link, "pubDate": pub, "source_name": source_name, "source_url": source_url})
    return out

def is_recent(pub_date, days=3):
    if not pub_date:
        return True
    try:
        dt = parsedate_to_datetime(pub_date)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt >= datetime.now(timezone.utc) - timedelta(days=days)
    except Exception:
        return True

def similar(a, b):
    return SequenceMatcher(None, norm(a), norm(b)).ratio() >= 0.72

def relevant(title, topic):
    q = norm(topic.get("query", topic.get("name","")))
    name = norm(topic.get("name",""))
    t = norm(title)

    if name == "maldives":
        return ("maldives" in t or "maldivian" in t) and not any(term in t for term in NOISE_TERMS)

    tokens = [x for x in re.findall(r"[a-z0-9]+", q) if len(x) > 2 and x not in {"and","or"}]
    if not any(tok in t for tok in tokens):
        return False

    # Sports/entertainment noise stays out unless explicitly requested as a topic.
    if topic.get("category") not in {"sports","entertainment"} and any(term in t for term in NOISE_TERMS):
        return False

    return True

def classify(title, default_category):
    t = norm(title)
    if any(term in t for term in SENSITIVE_TERMS):
        return "politics", "review", 80
    return default_category, "auto", 92

def build_post(title, source_name):
    source = f" ({source_name})" if source_name else ""
    base = f"{title}{source}"

    # Keep the actual article URL in metadata/source, not in the post text.
    # This avoids ugly Google News redirect URLs and leaves room for a cleaner caption.
    if len(base) > 250:
        base = base[:249].rstrip() + "…"

    return base

def main():
    cfg = load(INTERESTS, {})
    queue = load(QUEUE, [])

    queue = [x for x in queue if x.get("id") not in {"welcome-1","review-example"}]

    existing_text = {fid(x.get("text","")) for x in queue}
    existing_titles = [x.get("source_title","") for x in queue if x.get("source_title")]
    blocked = {w.lower() for w in cfg.get("blocked_words", [])}
    limit = int(cfg.get("max_new_items_per_run", 8))
    added = 0

    topics = sorted(cfg.get("topics", []), key=lambda x: int(x.get("weight",0)), reverse=True)
    for topic in topics:
        if added >= limit:
            break
        try:
            items = fetch_items(topic.get("query", topic.get("name","")))
        except Exception as e:
            print("DISCOVERY_ERROR", topic.get("name"), type(e).__name__, str(e))
            continue

        for item in items:
            if added >= limit:
                break

            title = item["title"]
            hay = norm(title)

            if not is_recent(item.get("pubDate"), days=3):
                continue
            if any(w in hay for w in blocked):
                continue
            if not relevant(title, topic):
                continue
            if any(similar(title, old) for old in existing_titles):
                continue

            category, status, confidence = classify(title, topic.get("category","general"))
            post = build_post(title, item.get("source_name",""))
            if not post:
                continue

            fp = fid(post)
            if fp in existing_text:
                continue

            queue.append({
                "id": "disc-" + fp,
                "topic": category,
                "interest": topic.get("name", category),
                "text": post,
                "confidence": confidence,
                "status": status,
                "source": item["link"],
                "source_name": item.get("source_name",""),
                "source_url": item.get("source_url",""),
                "source_title": title,
                "discovered_at": item.get("pubDate","")
            })
            existing_text.add(fp)
            existing_titles.append(title)
            added += 1

    save(QUEUE, queue)
    print(f"DISCOVERY_ADDED={added}")
    print(f"QUEUE_TOTAL={len(queue)}")

if __name__ == "__main__":
    main()
