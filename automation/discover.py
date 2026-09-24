import json, hashlib, html, re, urllib.parse, xml.etree.ElementTree as ET
from pathlib import Path
import requests

ROOT = Path(__file__).resolve().parents[1]
QUEUE = ROOT / "automation" / "queue.json"
INTERESTS = ROOT / "automation" / "interests.json"

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
    r = requests.get(url, timeout=20, headers={"User-Agent":"PublisherStudioAutopilot/1.0"})
    r.raise_for_status()
    root = ET.fromstring(r.text)
    out = []
    for item in root.findall(".//item"):
        title = clean_title(item.findtext("title"))
        link = (item.findtext("link") or "").strip()
        pub = (item.findtext("pubDate") or "").strip()
        if title and link:
            out.append({"title": title, "link": link, "pubDate": pub})
    return out

def build_post(title, link):
    base = title.strip()
    suffix = f"\n{link}"
    max_title = 280 - len(suffix)
    if max_title < 40:
        return ""
    if len(base) > max_title:
        base = base[:max_title-1].rstrip() + "…"
    return base + suffix

def main():
    cfg = load(INTERESTS, {})
    queue = load(QUEUE, [])
    existing = {fid(x.get("text","")) for x in queue}
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
            hay = norm(item["title"])
            if any(w in hay for w in blocked):
                continue

            post = build_post(item["title"], item["link"])
            if not post:
                continue
            fp = fid(post)
            if fp in existing:
                continue

            category = topic.get("category","general")
            sensitive = category in {"politics","breaking-news"}
            queue.append({
                "id": "disc-" + fp,
                "topic": category,
                "text": post,
                "confidence": 90 if not sensitive else 80,
                "status": "review" if sensitive else "auto",
                "source": item["link"],
                "source_title": item["title"],
                "discovered_at": item.get("pubDate","")
            })
            existing.add(fp)
            added += 1

    if added:
        save(QUEUE, queue)
    print(f"DISCOVERY_ADDED={added}")

if __name__ == "__main__":
    main()
