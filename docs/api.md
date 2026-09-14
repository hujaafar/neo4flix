# REST API

All paths use the same HTTPS origin. Unless marked anonymous, supply `Authorization: Bearer <access-token>`. Request bodies are JSON, IDs are opaque strings, dates use `YYYY-MM-DD`, and scores are integers from 1 through 5. Personal endpoints derive ownership from the verified JWT subject; there is no caller-supplied user ID.

Validation failures return 400; authentication failures 401; denied actions 403; missing records 404; uniqueness conflicts 409; throttling 429; unavailable REST dependencies 503. Application errors use `{ "status": 400, "message": "..." }`. Spring Security and gateway errors can have empty/non-JSON bodies, which the frontend handles. Empty writes return 204 where noted.

Unsupported methods return 405 with an `Allow` header; unsupported request media types return 415; unacceptable response types return 406. Malformed JSON and invalid parameter types remain 400. Unexpected server failures return a generic 500 without internal details.

## Authentication — user service

Every auth POST requires an `Origin` header exactly matching `APP_ORIGIN`. These endpoints are anonymous and must not receive an expired or malformed bearer token. JSON content types, strict Origin validation, a Secure HttpOnly SameSite=Strict cookie, and no permissive CORS protect cookie-authenticated refresh/logout from CSRF.

| Method | Path                                                     | Body / result                                                                                                    |
| ------ | -------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| POST   | `/api/auth/register`                                     | `{name,email,password}` → 201 `{accessToken,expiresIn,user}` + refresh cookie                                    |
| POST   | `/api/auth/login`                                        | `{email,password,code?}` → token response; `code` required if 2FA is enabled                                     |
| POST   | `/api/auth/refresh`                                      | Refresh cookie → rotated cookie and new access token                                                             |
| POST   | `/api/auth/logout`                                       | Refresh cookie → 204; revokes all account sessions and clears cookie                                             |
| GET    | `/api/auth/oauth2/providers`                             | Google and GitHub availability; no secrets                                                                       |
| GET    | `/api/auth/oauth2/authorize/google?returnUrl=/watchlist` | Start Google authorization-code flow (browser redirect)                                                          |
| GET    | `/api/auth/oauth2/authorize/github?returnUrl=/watchlist` | Start GitHub authorization-code flow (browser redirect)                                                          |
| GET    | `/api/auth/oauth2/callback/google`                       | Spring validates code/state/PKCE/ID token and redirects to completion                                            |
| GET    | `/api/auth/oauth2/callback/github`                       | Spring validates code/state/PKCE; GitHub user and verified-primary-email APIs identify the account               |
| GET    | `/api/auth/oauth2/pending`                               | Temporary browser-bound proof → `{provider,providerName,mode,email,name,twoFactor,returnUrl}`                    |
| POST   | `/api/auth/oauth2/complete`                              | `{name?,password?,code?}` → existing token response; password required for signup/linking, code when 2FA enabled |
| POST   | `/api/auth/oauth2/cancel`                                | Invalidates the temporary OAuth proof → 204                                                                      |

Access JWTs last 15 minutes, use RS256, issuer `neo4flix`, audience `neo4flix-api`, an opaque user subject, a `roles` array, and account token version `ver`. Refresh families have a fixed seven-day lifetime; rotation does not extend the server-side expiry. The browser holds access tokens in memory and restores sessions with the refresh cookie. Logout, password changes, 2FA changes, refresh replay, and account deletion invalidate issued JWTs through the per-request account-version validator.

## Profile, security, watchlist — user service

| Method     | Path                                | Body / result                                                              |
| ---------- | ----------------------------------- | -------------------------------------------------------------------------- |
| GET        | `/api/users/me`                     | `{id,name,email,role,twoFactorEnabled,googleLinked,githubLinked}`          |
| DELETE     | `/api/users/me/oauth2/google`       | `{password,code?}` → 204; disconnects Google and revokes sessions          |
| DELETE     | `/api/users/me/oauth2/github`       | `{password,code?}` → 204; disconnects GitHub and revokes sessions          |
| PATCH      | `/api/users/me`                     | `{name}` → updated profile                                                 |
| DELETE     | `/api/users/me`                     | `{password,code?}` → 204, permanent account data deletion                  |
| PUT        | `/api/users/me/password`            | `{password,newPassword,code?}` → 204, requires login again                 |
| GET        | `/api/users/me/ratings`             | Rating history, delegated to rating service over REST                      |
| POST       | `/api/users/me/2fa/setup`           | `{password}` → `{secret,uri}`; pending enrollment expires in ten minutes   |
| POST       | `/api/users/me/2fa/confirm`         | `{password,code}` → 204, activates pending enrollment and revokes sessions |
| DELETE     | `/api/users/me/2fa`                 | `{password,code}` → 204, disables 2FA and revokes sessions                 |
| GET        | `/api/users/me/watchlist`           | Movies, newest addition first; includes `watchlistNote`                    |
| POST / PUT | `/api/users/me/watchlist/{movieId}` | Optional `{note}` → 204; idempotent add/update                             |
| DELETE     | `/api/users/me/watchlist/{movieId}` | 204; idempotent removal                                                    |

Password policy: at least 12 characters, uppercase, lowercase, number, symbol, and at most 72 UTF-8 bytes. BCrypt cost is 12. TOTP follows RFC 6238, SHA-1, six digits, 30-second steps, ±1 step clock tolerance, and transactionally stored replay prevention. Login and sensitive account actions share a five-failure lockout for 60 seconds. Successful authenticator use consumes the time step; wait for the next code before another security action.

## Catalogue — movie service

| Method | Path                          | Body / result                                                                                                                             |
| ------ | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/movies`                 | Array of movie objects; query parameters below                                                                                            |
| GET    | `/api/movies/genres`          | Sorted unique genre strings                                                                                                               |
| GET    | `/api/movies/graph`           | **ADMIN** → bounded live `{nodes,relationships,movieLimit,userLimit,gdsVersion}` snapshot; no emails, credentials or private rating notes |
| GET    | `/api/movies/{id}`            | One movie, computed `averageRating` and `ratingCount`                                                                                     |
| GET    | `/api/movies/{id}/related`    | Up to six movies sharing genre nodes                                                                                                      |
| GET    | `/api/movies/recommendations` | Delegates to recommendation service over REST                                                                                             |
| POST   | `/api/movies`                 | **ADMIN** → 201, new movie                                                                                                                |
| PUT    | `/api/movies/{id}`            | **ADMIN** → updated movie                                                                                                                 |
| DELETE | `/api/movies/{id}`            | **ADMIN** → 204; removes attached graph relationships                                                                                     |

List filters: `q` (case-insensitive title/genre or exact year), `genre` (canonical genre), `year`, inclusive `from`/`to`, zero-based `page` (default 0), `size` (default 24, maximum 100). Sort is title ascending. Negative pagination, invalid dates, and inverted ranges are rejected.

Create/update example:

```json
{
  "title": "A New Story",
  "releaseDate": "2024-06-01",
  "genres": ["Drama", "Adventure"],
  "director": "Example Director",
  "runtime": 110,
  "overview": "A short synopsis.",
  "artwork": "default"
}
```

`artwork` is a bundled local asset slug, never an arbitrary URL or HTML. Custom unknown slugs use the default illustration in the frontend. CRUD responses include `id` and `year`. A zero `ratingCount` means no community ratings; the UI does not present the numeric zero average as an audience rating.

## Ratings — rating service

| Method     | Path                        | Body / result                                             |
| ---------- | --------------------------- | --------------------------------------------------------- |
| GET        | `/api/ratings/me`           | Own rating array with nested `movie`, newest update first |
| GET        | `/api/ratings/me/{movieId}` | Own rating or 404                                         |
| POST / PUT | `/api/ratings/me/{movieId}` | `{score,review?}` → upserted rating                       |
| DELETE     | `/api/ratings/me/{movieId}` | 204 or 404                                                |

Rating properties: `id`, `score`, private `review`, ISO `createdAt`, ISO `updatedAt`. The score and review are validated server-side. A user-node write lock precedes relationship MERGE, making simultaneous upserts safe without duplicate ratings.

## Recommendations and sharing — recommendation service

| Method     | Path                                       | Body / result                                            |
| ---------- | ------------------------------------------ | -------------------------------------------------------- |
| GET        | `/api/recommendations`                     | Ranked movies with `recommendationScore` and `reason`    |
| GET        | `/api/recommendations/dismissed`           | Own hidden movies                                        |
| POST / PUT | `/api/recommendations/dismissed/{movieId}` | 204; hide/update hidden timestamp                        |
| DELETE     | `/api/recommendations/dismissed/{movieId}` | 204; restore                                             |
| GET        | `/api/recommendations/shares`              | Shares owned by current account                          |
| POST       | `/api/recommendations/shares`              | `{movieId,note}` → 201 share                             |
| GET        | `/api/recommendations/shares/{id}`         | Any authenticated holder of this opaque link can read it |
| PUT        | `/api/recommendations/shares/{id}`         | Owner only, `{note}` → updated share                     |
| DELETE     | `/api/recommendations/shares/{id}`         | Owner only → 204, revoke link                            |

Recommendation filters: canonical `genre`, inclusive `from`/`to`, and `limit` (default 24, maximum 100). Calculated recommendations are read-only results; CRUD in this service operates on saved share records and hide/restore preferences. Shares contain `id`, `note`, `createdAt`, and nested movie data, with no owner email, password, or rating history. Share URLs are `/share/{id}` and require login.

## Health

Every Spring Boot service exposes `/actuator/health` inside the private network, without details. It is not routed through the public gateway. The container probe checks the endpoint and Neo4j connectivity before the frontend starts.
