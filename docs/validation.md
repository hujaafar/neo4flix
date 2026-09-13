# Validation report

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
