# Real movie data from TMDB

Neo4flix can replace its offline starter catalogue with up to 50 real films from TMDB’s popular or top-rated collections. It imports titles, release dates, genres, synopses, runtime, directors, poster paths and community scores. Your recommendations continue to use Neo4j and your own ratings.

## Configure your token

1. Create a TMDB account and open [API settings](https://www.themoviedb.org/settings/api).
2. Request developer access for your noncommercial educational application. Review the current terms and enter your own contact details. Use your actual deployment URL (for this local setup, `https://localhost:9443`).
3. Copy **API Read Access Token**, not the shorter API Key. Keep it private.
4. In the project directory, run `./scripts/configure-tmdb.ps1` in PowerShell and paste the token into the hidden prompt. On other systems, add `TMDB_READ_ACCESS_TOKEN=your-token` to the ignored `.env` file.
5. Build the updated app with `docker compose up -d --build --wait`. If the updated image is already built, apply a token change with `docker compose up -d --no-deps --wait movie-service`.

Only movie-service receives the token. It uses outbound HTTPS to the fixed TMDB API origin, verifies TLS and does not follow redirects. It never sends your Neo4flix profile or rating history to TMDB. Do not commit `.env` or `secrets/`.

## Preview and replace

Sign in as an administrator and open **Manage films**. Select a collection and film count, then choose **Fetch a preview**. This runs as a bounded background job; existing films stay available. The server fetches complete details and credits, skips adult or incomplete records, and requires the full requested count before allowing replacement.

Review the titles and posters. Choose **Replace catalogue with these films** when ready. The transaction saves the old movies, genres, shares and related relationship properties in a private `CatalogBackup` node before deleting the old collection. Accounts, passwords, OAuth links and 2FA settings are preserved. Old movie ratings, watchlists, hidden picks and share links are reset. Failed transactions roll back all changes. A replacement cannot be applied twice.

Jobs are admin-owned, cached in memory (at most eight), and expire after 30 minutes or a service restart. Fetch another preview if one expires. Provider failures or rate limits leave the current catalogue intact.

## Backups

`CatalogBackup.payload` stores JSON with movies, genres, shares and relationship endpoints/properties. It excludes user profile and credential properties. It contains private rating notes and is deliberately excluded from HTTP responses and the visual graph. Keep database dumps and exports private. A database operator can restore the catalogue from this payload in a transaction after stopping application writes; do not paste its contents into a public issue or commit it.

Before a substantial catalogue replacement, also keep a consistent database backup. Stop the stack with `docker compose stop` (never `down -v`), then copy `/data` from `neo4flix-neo4j-1` into a private backup folder using `docker cp`. Resume with `docker compose up -d --wait`. Full database restoration should happen with Neo4j stopped and matching Neo4j versions; it also restores accounts and interactions to the backup time.

## Posters and attribution

Images use TMDB’s fixed image CDN and fall back to local artwork if unavailable. Community scores from TMDB are labeled **out of 10**; your Neo4flix ratings stay **out of 5**. The cinema entrance uses only public movie metadata. API requests and authentication credentials never run in the browser.

The public **Movie data & credits** page includes the approved TMDB logo and required attribution: “This product uses the TMDB API but is not endorsed or certified by TMDB.” Posters belong to their respective rights holders.

References: [TMDB authentication](https://developer.themoviedb.org/docs/authentication-application), [image basics](https://developer.themoviedb.org/docs/image-basics), [attribution](https://developer.themoviedb.org/docs/faq), [approved logos](https://www.themoviedb.org/about/logos-attribution).
