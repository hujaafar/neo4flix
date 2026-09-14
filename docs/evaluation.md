# Evaluation walkthrough

Use the latest submitted commit from the dedicated repository. A Git commit is immutable: resetting to `17d232dc887bb04e6b8a3a3348867949b1435e7f` deliberately restores the earlier implementation, without the audit fixes. Update the evaluation's selected revision before assessing the corrected project.

```powershell
git clone https://learn.reboot01.com/git/hujaafar/neo4flix.git
cd neo4flix
git rev-parse HEAD
.\scripts\start.ps1 -TrustLocalCertificate
docker compose ps
```

If evaluating a specified revision, use a separate clean clone and `git checkout --detach <submitted-commit>` there. Do not hard-reset a working directory that contains your changes.

## Demonstrate the application

1. Open `https://localhost:8443`, register a normal account and sign in. Show title search, genre/year filters and the release-date range.
2. Open Inception. Show release date, genres and actual average rating. Save a rating, change it and show **My ratings**. A 0 or 6 rating must be rejected.
3. Open **For you**. Rated and hidden movies are excluded. Explain the genre and collaborative reasons. Hide and restore a suggestion, then filter by genre/date.
4. Save/remove a watchlist movie. Create a shared recommendation, copy its link, open it as another signed-in user, edit the note and revoke the link. Private rating notes remain private.
5. Show profile edits and authenticator enrollment under **Account & security**. Confirm enrollment with an authenticator, sign out and demonstrate that a password alone cannot sign in. Use a fresh TOTP code for each security action.
6. Sign in as the local administrator using the generated credentials in the private `.env`. Create/edit/delete a temporary movie. Open **Database graph**, select a viewer, and inspect the actual `RATED` edge, score and timestamps. Select a film to inspect `IN_GENRE` edges. Normal accounts cannot access this endpoint.
7. Run the API and bounded stress suites below on a development dataset. The tests create and remove their own accounts; do not run them against a live production database.

```powershell
docker compose run --rm test-api
docker compose run --rm test-stress
```

## Explain the code

| Topic | Code and explanation |
| --- | --- |
| OGM | `common/.../model/`, `Ogm.java`, `MovieStore.java`, `RatingStore.java`: annotated nodes and relationship entities, transaction-scoped identity maps, UUID IDs and mapped persistence. |
| Graph algorithm | `RecommendationController.java`: GDS Jaccard set similarity weights peers, genre traversal adds affinity, and a prior-weighted audience term supplies cold-start results. |
| Worked score | Alice likes A/B and Bob likes A/C. Jaccard is 1 intersection / 3 union = 1/3. Bob contributes 1/3 toward C before the other ranking terms. The integration fixture checks this value. |
| REST service communication | Movie service delegates personalized recommendations to the recommendation service; user service delegates rating history to the rating service with the validated JWT. |
| Authorization | Shared JWT validation checks signature, expiry, role and current account token version. Every personal record lookup includes the verified subject. |
| Passwords and 2FA | BCrypt password hashes, enforced password complexity, encrypted TOTP secrets and replay prevention. Security changes revoke sessions. |
| HTTPS | Caddy issues the local development certificate. Trust the exported CA for localhost. The production override requests a public certificate only after a real domain/server are configured. |
| Errors and concurrency | Framework errors retain 400/404/405/406/415 statuses and appropriate headers. Rating writes take a per-user graph lock, and concurrent updates must leave one relationship. |
| Limitations | Shared graph ownership, per-request ranking suitable for the starter catalogue, one-host deployment and no production-capacity claim. |

See [architecture](architecture.md), [API contracts](api.md) and [recorded validation](validation.md) for the detailed evidence.

## Questions that require people

Attendance, each member's actual contribution, understanding, equal task allocation and future applications cannot be proven by automated tests. Each member should prepare their own truthful contribution account, demonstrate the relevant code and explain one tradeoff. Do not answer these checklist items automatically based on a passing build. Usability observations and a bounded load run also do not replace an evaluator's independent review.
