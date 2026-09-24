# X Autopilot v1

Free GitHub Actions-based publishing engine for Publisher Studio.

## Safety defaults
- DRY_RUN is on by default.
- Political and breaking-news categories require manual review.
- Confidence below 85 is held.
- Duplicate text is blocked after a successful live post.
- Likes and follows are never automated.

## Schedule
The workflow runs four times per day. GitHub cron uses UTC.

## Enable live posting
Create these GitHub repository secrets:
- X_API_KEY
- X_API_SECRET
- X_ACCESS_TOKEN
- X_ACCESS_TOKEN_SECRET

Then create repository variable:
- X_AUTOPILOT_DRY_RUN = false

Your X developer app must have permission to create posts.

## Queue
Edit `automation/queue.json`. Only items with:
- `status: "auto"`
- confidence >= 85
- non-sensitive category
- text <= 280 characters
can publish.

The browser dashboard at `x-autopilot.html` is currently a local planning UI. It does not write back to GitHub yet.
