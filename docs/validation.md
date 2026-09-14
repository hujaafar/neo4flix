# Validation report

## Normal-user walkthrough and expanded verification — 14 September 2026

The manual walkthrough and expanded browser suite found and fixed an offscreen share form, misleading empty states on failed collection requests, and a missing accessible name on the mobile account link. The share form now explains the localhost-only scope of local links. See [the full normal-user report](user-walkthrough.md) for the feature checklist and verification limits.

The final deployed frontend passed **22/22 desktop/mobile browser scenarios in 224.79 seconds**, with no skips or flaky results; **58/58 live HTTPS API checks in 10.31 seconds**; and **700/700 load responses** at 4/8/16 workers (p95 85.6/82.0/94.0 ms). The production Angular build and formatting checks passed. Only the frontend container was replaced; all seven containers remain running and the database/four APIs report healthy. Existing data was preserved and the final scoped check found zero leftover walkthrough accounts. The controlled collection-error tests simulate only the failed response; retries and all other successful feature requests use the live database.

## Continuous reel motion and scroll gap fix — 14 September 2026

The shared 3D reel now stays animated while visible on the public entrance and signed-in Discover page, including with reduced-motion preferences enabled. Manual pause controls were removed at the owner's request. Keyboard skip links, offscreen/background suspension, renderer cleanup and graphics fallback remain; other page transitions still respect reduced motion.

Discover previously pinned an `86svh` stage, exposing an empty strip below it. A regression check against the previous deployment reproduced this at 860px in a 1000px viewport. Both reels now use `100dvh`, with no minimum-height override that can exceed a short viewport.

- Angular production build and formatting checks passed.
- All **14 desktop/mobile browser scenarios passed in 78.50 seconds**, with no failures, skips or flaky results and normal HTTPS certificate validation.
- New checks cover full viewport coverage at three signed-in scroll positions, viewport resizing, actual moving film texture under reduced motion, live preference changes, reloads and Angular remounts. Existing keyboard skip, graphics fallback, no-JavaScript and account journeys also passed.
- Desktop and mobile scroll screenshots were inspected. The blank strip no longer appears while the reel is pinned.

The Docker compilation attempt exhausted available memory alongside the running services and was cancelled. The Windows-built production bundle was then packaged into the same nginx runtime and deployed successfully, replacing only the frontend container. HTTPS returned 200 afterward. Database volumes were preserved. Backend unit/API/stress checks below describe the earlier rebuild and were not rerun for this frontend-only change. Mobile checks use browser emulation, not a physical phone.

## Fresh Docker rebuild and live recheck — 14 September 2026

Docker Desktop 4.90.0 was reinstalled and its Linux engine 29.7.2 started successfully. This was a clean rebuild from source and the 18-film seed catalogue; previous Docker volume contents were not restored. The existing local project secrets were retained.

The complete Windows startup helper succeeded at `https://localhost:9443`, including CA export, Windows trust and a normal verified HTTPS request. The in-app browser loaded and displayed the live cinema entrance without a certificate warning. The earlier connection-reset and port-conflict blockers described below are resolved for this deployment.

| Fresh check | Result |
| --- | --- |
| Docker Maven build | All four services built; 14 unit tests passed, none failed or skipped |
| Angular production build | Passed inside the frontend image build |
| Live HTTPS API suite | 58 assertions passed in 16.87 seconds, with certificate and hostname validation |
| Bounded stress suite | 700/700 successful responses and content checks; rating uniqueness and account cleanup passed |
| Desktop/mobile browser suite | 14/14 passed in 74.95 seconds; no failures, skips or flaky results |
| Browser TLS | Certificate validation enabled in every Playwright context, including the no-JavaScript scenario; Node requests used the development and already trusted inspection CAs |
| Visible UI review | Live in-app cinema entrance and live desktop/mobile graph screenshots inspected |
| Restart after database dump | Database and API health checks passed; Windows HTTPS returned 200 |

The browser suite covers motion and reduced-motion preferences, real film links, genre selection through registration, no-JavaScript content, pause/resume, keyboard skip, graphics fallback, account journeys and the live administrator graph. The API and graph scenarios confirm actual stored ratings, access restrictions and absence of private profile/review data; administrator traces are disabled.

| Concurrent workers | Requests | p50 | p95 | Maximum | Failed responses/content checks |
| --- | --- | --- | --- | --- | --- |
| 4 | 100 | 78.4 ms | 95.5 ms | 150.8 ms | 0 |
| 8 | 200 | 78.0 ms | 118.6 ms | 174.1 ms | 0 |
| 16 | 400 | 73.7 ms | 91.8 ms | 105.8 ms | 0 |

An offline dump of the fresh database was saved outside Docker before restarting it. This is a backup of the reconstructed database, not recovery of the previous volumes; a restore drill was not performed. The short stress run is not a large-catalogue or endurance benchmark. Public ACME issuance, production capacity, physical-device GPU performance and human evaluation questions remain outside these checks.

This laptop shares a 4 GB WSL runtime with another project. Run their full stacks sequentially to avoid memory exhaustion. The results below remain historical records of earlier attempts.

## Audit fixes — 14 September 2026

The corrected source adds actual Neo4j-OGM persistence, the GDS Jaccard recommendation signal, an administrator graph view, framework HTTP error handling, LF checkout rules, a Windows startup/trust helper and a repeatable stress harness. The earlier pinned revision `17d232d` does not contain these changes.

Verified against a separate Compose project and fresh graph at `https://localhost:8943`:

- **14 unit tests passed** in the Docker Maven build: six security primitive tests, five MVC error tests, OGM bootstrap and two transaction retry/cleanup tests.
- **58 API assertions passed** in 14.86 seconds through Caddy with certificate and hostname validation enabled. This includes mapped movie/genre readback, removed genre edges, rating CRUD/concurrency, GDS similarity of exactly 1/3, graph access/privacy, all previous account flows and real TOTP checks.
- **700/700 stress responses passed** with HTTP 200 and valid content. The harness confirmed one rating relationship after concurrent updates and deleted its disposable account.
- Angular production build, Prettier and Ruff format checks passed.
- A fresh index checkout with Windows `core.autocrlf=true` passed the ordinary formatting command; `.gitattributes` fixes the earlier 64-file line-ending failure.

**Remaining host verification:** the 14 desktop/mobile browser scenarios attempted the isolated HTTPS origin but failed at navigation with connection resets, before testing application behavior. The local main-stack rebuild then reached healthy database/API services but could not bind port 8443 because another local project used that port. The Docker engine subsequently stopped responding. These browser runs are failures, not passes. Neo4flix now supports configurable ports; this laptop's ignored `.env` selects HTTPS 9443, HTTP 9080 and a matching `APP_ORIGIN`. Fresh browser and Windows certificate verification at that address still require a responsive Docker engine and coordinated testing on the shared host.

The graph page additionally passed two isolated Chrome checks at 1440px and 390px: keyboard selection, rating/table display, no outer overflow and recovery from a 503 followed by refresh. These used the production Angular bundle with explicitly mocked auth/graph responses on a local static server, and were visually inspected. They do not replace the pending live Docker browser suite. The Windows startup helper and optional build CA configure successfully, but a complete successful run of that helper and live browser graph inspection are not yet claimed. No publicly trusted ACME certificate or real-user recommendation relevance benchmark was evaluated.

| Concurrent workers | Requests | p50 | p95 | Maximum | Failed responses/content checks |
| --- | --- | --- | --- | --- | --- |
| 4 | 100 | 75.4 ms | 95.3 ms | 154.3 ms | 0 |
| 8 | 200 | 74.0 ms | 89.2 ms | 109.8 ms | 0 |
| 16 | 400 | 77.1 ms | 104.1 ms | 127.6 ms | 0 |

The load mixes catalogue, recommendations, movie details and rating upserts equally, using one account and the 18-film starter catalogue. It is a short contention test, not an endurance test, large-data benchmark or proof of production capacity.

The live tests caught and drove fixes for a GDS folder permission problem, an incomplete Maven cache from the full disk, OGM deadlock retries and stale genre edges caused by loading an OGM snapshot before a write query cleared it. The API harness also now avoids racing a TOTP period boundary. Earlier failed runs are not counted as passes.

The results below are historical and retain their original scope. For evaluation steps and questions that require people, see [the walkthrough](evaluation.md).

## Original validation — 9 September 2026

Validated locally on **9 September 2026** against the Docker deployment at `https://localhost:8443`.

| Check | Result |
|---|---|
| Maven compilation and executable packaging | All four Spring Boot services and shared module built |
| Security primitive unit tests | 6 passed: RFC TOTP vectors, clock tolerance, replay rejection, password boundaries, authenticated encryption, SHA-256 vector |
| Real HTTPS API integration | 52 assertions passed against Neo4j and the four running services |
| Desktop Chromium journey | Passed at 1440 × 1000 |
| Mobile Chromium journey | Passed using iPhone 13 viewport/emulation |
| Frontend production bundle | Built with lazy-loaded pages and CSP-compatible external stylesheets |
| Frontend dependency audit | `npm audit`: 0 known vulnerabilities reported |
| Docker readiness | Neo4j and all four Spring services healthy; frontend and TLS gateway running |
| Nginx configuration | Syntax validation passed |
| Production Compose override | Configuration validation passed with placeholder domain/contact values |

API checks cover authenticated catalogue access, password policy, unique email handling, role restrictions, tampered JWTs, parameterized search input, filters, pagination validation, rating range and movie existence, concurrent rating upserts, account ownership, REST delegation, recommendation exclusion/ranking/reasons, hidden picks, watchlist CRUD, share ownership/revocation, profile updates, refresh rotation and replay revocation, CSRF Origin rejection, logout, password changes, real TOTP verification, replay rejection, admin CRUD, and account deletion.

Each browser test creates a fresh account and exercises sign-up, catalogue loading, computed CSS grid layout, absence of horizontal overflow, title search, film details, watchlist saves, ratings with private notes, sharing, route navigation, persistence after reload, recommendations, sign-out/sign-in, and account deletion. Both collect browser exceptions and fail on JavaScript errors. They also capture discovery and recommendation screenshots.

During validation, real failures were corrected: query whitespace and explicit aggregation grouping, stale reverse-proxy service addresses after container recreation, Angular deferred CSS incompatible with a strict CSP, a watchlist-loading race, and date-only formatting across time zones. The final suites passed after these corrections.

## Scope of the result

The API suite verifies the actual Caddy certificate chain inside Docker. On this Windows machine, AVG HTTPS inspection replaces an untrusted local certificate; the browser tests use their isolated development-certificate exception. Host-wide trust and antivirus settings were not changed. The local CA export and optional trust command are documented in the README.

The production override was validated as configuration only. Public DNS, public certificate issuance, a remote host, real-user usability studies, load testing, accessibility certification, container-image vulnerability scanning, and an independent penetration test were not performed. The frontend dependency audit is a point-in-time report, not a guarantee that all components are free of vulnerabilities.

Run `docker compose run --rm test-api` to reproduce API results. Run `npm run test:e2e` from `frontend` to reproduce desktop/mobile browser results. Raw reports are generated in ignored test-result directories and do not contain signing keys or administrator passwords.

## Follow-up: local certificate trust

After the user reported `ERR_CERT_AUTHORITY_INVALID`, the running gateway's CA certificate was exported again and matched to the saved certificate. It was installed into this user's Windows `CurrentUser\Root` store. Both `/login` and `/register` then returned HTTP 200 through Windows with normal HTTPS verification enabled. Antivirus settings and certificate validation were not disabled. The running Codex browser continued to report the certificate error, including in a fresh tab; restarting Codex is the next user step. Browser success after that restart has not yet been verified.

## Follow-up: scroll-craft integration

The frontend now includes `/experience/`, a scroll-craft gallery entrance, and a matching animated Discover feature. The unmodified upstream engine runs once in the entrance document; Angular effects use lifecycle-managed directives. Genre selections carry through login and registration into the database-backed catalogue.

The updated production build and local Docker frontend deployment passed. All **10 Playwright tests passed**, covering desktop/mobile user journeys, real film destinations, genre selection through registration, signed-in return, pointer pixels, keyboard focus, live reduced-motion changes, compact layout and the no-JavaScript entrance. The upstream harness reviewed **57 final frames** across desktop, mobile and reduced motion with no browser errors, failed requests, or detected dead scroll. Contact sheets and intermediate frames were visually reviewed. No physical phone was tested.

Installed Chrome loaded the final HTTPS entrance during these harness runs with ordinary certificate verification enabled. The earlier in-app browser cache issue was not independently rechecked. Browser test account cleanup was corrected after two runs encountered separate-client loopback/TLS problems; all temporary accounts were removed and the final complete suite passed. Detailed decisions and limitations are in `scrollcraft/builds/neo4flix/REPORT.md`. Previous backend results above are from the original build; backend code was not changed or retested in this update.

## Follow-up: large 3D motion from the video reference

The public entrance and authenticated Discover now share a real 3D reel, moving film strip, independent landscape and camera transition. All 12 desktop/mobile scenarios passed across the full run (11) and the targeted long-test rerun (1). The long desktop capture hit its former 60-second test allowance; after disabling continuous trace screenshots and increasing the allowance, its unchanged assertions passed in 54.8 seconds. Motion, camera distance, pause/resume, compact layout, reduced motion, keyboard skip, graphics context loss, no-JavaScript content and Angular renderer disposal are covered alongside complete account journeys.

The upstream harness captured 58 frames with no browser errors, failed requests or detected dead scroll. Actual desktop/mobile and static fallback compositions were visually inspected. An H.264 recording of the live browser is included in the delivery. Local Angular and Docker builds passed; all services remained running, and Nginx syntax passed. Installed Chrome used ordinary HTTPS certificate verification. Physical-device GPU performance remains untested. The previous backend results are historical; no backend changes were made in this revision. Full details and asset provenance are in `scrollcraft/builds/neo4flix/REPORT.md`.


## Follow-up: readable source formatting

The user identified overly compressed source in `frontend/src/pages/account.ts`. Editable Angular templates, TypeScript, CSS, Java, XML, YAML and the Python API test are now indented and split into readable statements. The account component expanded from 26 dense lines to 287 formatted lines. Formatting configuration, pinned development tools and documented commands keep future edits consistent; generated bundles and upstream vendor files are excluded.

Validation: Prettier format check passed; Ruff format check passed; the Python AST is identical before/after formatting; upstream runtime bytes are unchanged. Maven verify compiled and packaged all backend modules with all six security unit tests passing. The Angular production build and local frontend Docker rebuild passed. The existing desktop register/search/rate/watchlist/share/refresh/delete journey passed against that rebuilt frontend in 21.4 seconds. This follow-up did not rerun the entire historical 12-case motion suite or 52 API assertions. No functional changes were intended.
