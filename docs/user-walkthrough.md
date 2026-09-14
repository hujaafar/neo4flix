# Neo4flix normal-user verification

14 September 2026. **The tested local workflows pass after three UI fixes.** The application remains running at `https://localhost:9443`.

## What was fixed

- **Sharing:** opening “Share this film” previously placed the form below the viewport. It now brings the form into view and focuses the note. Closing it returns focus to the sharing button.
- **Collection errors:** failed requests previously displayed an empty-collection message as well as an error. Watchlist, My ratings and Shared picks now offer “Try again” and only show empty states after a successful response.
- **Mobile account access:** the profile link now retains a descriptive accessible name when the layout shows only the user's initial.

The share form also explains that a localhost link works only on this computer. Friends on other devices need a deployment with a reachable HTTPS address.

## Checked through the application

A manual walkthrough in the in-app browser used a separate normal account. It followed Discover → movie details → share creation → Shared picks → edit/copy → shared detail, and checked watchlist addition/removal and refresh. The expanded Playwright suite used visible page controls and separate owner/recipient browser sessions against the live Docker application on desktop and mobile layouts.

| Area | Result and scope |
| --- | --- |
| Registration and login | Passed: required fields, weak-password rejection, sign-up, sign-in, sign-out, refresh, and return to a shared pick after recipient registration. |
| Discover and details | Passed: case-insensitive title search, genre/year search, combined genre/date filters, invalid ranges, empty results, reset, release date, director and actual film destinations. |
| Ratings | Passed: create, edit, reload, delete, notes displayed as text, and another user's private rating denied by the API. |
| Watchlist | Passed: add, list, remove and persistence after refresh; API checks include note updates and ownership. |
| Recommendations | Passed: genre/date filtering, already-rated exclusions, hide/restore, ranking and controlled collaborative/GDS similarity checks. |
| Sharing | Passed: empty state, create a note/link, clipboard copying in the manual walkthrough, separate recipient signup, edited note visible to recipient, private data excluded, cancel/revoke, and revoked-link error. Non-owners cannot edit/revoke through the API. |
| Account and 2FA | Passed: profile update, password change, old-password rejection, authenticator setup, wrong-code rejection, login with a fresh code, disable 2FA, and account deletion. API checks include code replay and session revocation. |
| Graph and movie administration | Passed: live administrator graph in desktop/mobile browsers; movie CRUD, OGM relationship replacement and graph privacy through the API. |
| Motion and navigation | Passed: reel progression, viewport coverage, keyboard skip, reduced-motion policy, remount/reload, graphics fallback and no-JavaScript entrance. |
| Failure recovery | Passed: each collection recovers from one simulated HTTP 503 via retry against the real database; an unknown movie displays an error with a working return link. |

## Final results

- **22/22 browser scenarios passed in 224.79 seconds**, with no failed, skipped or flaky results. Authenticator flows wait for fresh 30-second codes. Their traces and automatic screenshots are disabled.
- **58/58 live API checks passed in 10.31 seconds**, including JWT/role tampering, cookie security, cross-origin restrictions, refresh replay, private-data access and invalid inputs.
- **700/700 bounded load requests passed** at 4, 8 and 16 workers. The respective p95 latencies were 85.6, 82.0 and 94.0 ms. Concurrent rating uniqueness and account cleanup passed.
- Angular production build and source formatting passed. The production bundle was packaged into the nginx image and deployed by replacing only the frontend container.
- All seven application containers remained running, Neo4j and all four APIs reported healthy, and verified Windows HTTPS returned 200.
- Disposable walkthrough accounts were removed; a final scoped database check found **zero** remaining. Existing user accounts and data were not reset. Early harness cleanup errors were corrected before the final successful run, and those temporary accounts were explicitly cleaned up too.

Raw results are generated in `frontend/test-results/browser-results.json`, `test-results/api-results.json` and `test-results/stress-results.json`. Browser artifacts include recipient/revoked-link views, filtered recommendations and reel screenshots. Earlier failed regression attempts are not counted as passing tests.

## Practical limits

This verifies the local application, not a public deployment or a guarantee against every possible defect. Browser runs used installed Chrome desktop/mobile emulation plus a manual in-app-browser walkthrough, not physical phones or a full cross-browser matrix. The catalogue contains 18 films; large-catalogue pagination, endurance and production capacity were not benchmarked. Public certificate issuance and sharing from a different device still require a hosted address. Backend unit tests were not rerun for these frontend changes; the earlier build's 14 passing unit tests remain recorded separately in `validation.md`.
