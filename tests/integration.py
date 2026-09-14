"""End-to-end API checks against the running HTTPS Docker stack. Standard library only.
Creates isolated accounts and cleans them up. Never run against production data.
Usage: python tests/integration.py --ca secrets/local-ca.crt
"""

import argparse, base64, concurrent.futures, hashlib, hmac, http.client, json, os, secrets, socket, ssl, struct, time
from http.cookies import SimpleCookie
from pathlib import Path
from urllib.parse import urlsplit, quote

parser = argparse.ArgumentParser()
parser.add_argument("--base", default="https://localhost:8443")
parser.add_argument("--ca", default="secrets/local-ca.crt")
parser.add_argument(
    "--connect", help="Optional network host:port; TLS still verifies the hostname in --base"
)
args = parser.parse_args()
url = urlsplit(args.base)
context = ssl.create_default_context(cafile=args.ca)
context.load_default_certs()


class VerifiedConnection(http.client.HTTPSConnection):
    def connect(self):
        if not args.connect:
            return super().connect()
        host, port = args.connect.rsplit(":", 1)
        self.sock = context.wrap_socket(
            socket.create_connection((host, int(port)), timeout=self.timeout),
            server_hostname=url.hostname,
        )


report = []


def check(condition, label):
    if not condition:
        raise AssertionError(label)
    report.append(label)
    print("PASS", label, flush=True)


class Client:
    def __init__(self):
        self.token = ""
        self.cookie = ""
        self.email = ""
        self.password = ""
        self.deleted = False

    def request(
        self, path, method="GET", body=None, expected=200, origin=None, token=None, cookie=None
    ):
        conn = VerifiedConnection(url.hostname, url.port or 443, context=context, timeout=25)
        headers = {"Origin": args.base if origin is None else origin}
        bearer = self.token if token is None else token
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        if cookie is not None or self.cookie:
            headers["Cookie"] = "neo4flix_refresh=" + (self.cookie if cookie is None else cookie)
        if body is not None:
            headers["Content-Type"] = "application/json"
        conn.request(method, path, body=None if body is None else json.dumps(body), headers=headers)
        response = conn.getresponse()
        raw = response.read()
        status = response.status
        response_headers = dict(response.getheaders())
        conn.close()
        parsed = (
            json.loads(raw)
            if raw and response_headers.get("Content-Type", "").startswith("application/json")
            else raw.decode(errors="replace")
        )
        if status != expected:
            raise AssertionError(
                f"{method} {path}: expected {expected}, got {status}: {str(parsed)[:400]}"
            )
        if "Set-Cookie" in response_headers:
            cookies = SimpleCookie()
            cookies.load(response_headers["Set-Cookie"])
            if "neo4flix_refresh" in cookies:
                self.cookie = cookies["neo4flix_refresh"].value
        if isinstance(parsed, dict) and "accessToken" in parsed:
            self.token = parsed["accessToken"]
        return parsed, response_headers

    def register(self, label):
        self.email = f"{label}-{secrets.token_hex(6)}@example.test"
        self.password = "TestPass!" + secrets.token_hex(12)
        return self.request(
            "/api/auth/register",
            "POST",
            {"email": self.email, "password": self.password, "name": "Integration " + label},
            201,
        )

    def login(self, code=""):
        return self.request(
            "/api/auth/login",
            "POST",
            {"email": self.email, "password": self.password, "code": code},
            token="",
        )

    def delete(self):
        self.request("/api/users/me", "DELETE", {"password": self.password, "code": ""}, 204)
        self.deleted = True


def code(secret, step):
    key = base64.b32decode(secret)
    mac = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    offset = mac[-1] & 15
    return f"{(struct.unpack('>I', mac[offset : offset + 4])[0] & 0x7FFFFFFF) % 1000000:06}"


users = []
admin = Client()
created_movie = ""
started = time.time()
try:
    public = Client()
    _, headers = public.request("/login")
    check(
        "default-src" in headers.get("Content-Security-Policy", ""),
        "HTTPS UI has a restrictive CSP",
    )
    check("max-age=" in headers.get("Strict-Transport-Security", ""), "HTTPS UI sends HSTS")
    public.request("/api/movies", expected=401)
    check(True, "Anonymous catalogue request is denied")
    public.request(
        "/api/auth/register",
        "POST",
        {"name": "Bad Password", "email": "weak@example.test", "password": "weak"},
        400,
    )
    check(True, "Weak registration password is rejected")
    public.request(
        "/api/auth/register",
        "POST",
        {"name": "Cross site", "email": "evil@example.test", "password": "ValidPassword!123"},
        403,
        origin="https://evil.example",
    )
    check(True, "Cross-origin registration is denied")
    public.request("/api/auth/login", "PUT", expected=405)
    check(True, "Unsupported auth method returns 405 instead of a server error")
    alice = Client()
    users.append(alice)
    account, headers = alice.register("alice")
    check(account["user"]["role"] == "USER", "Registration always creates a USER role")
    check(
        all(flag in headers["Set-Cookie"] for flag in ["Secure", "HttpOnly", "SameSite=Strict"]),
        "Refresh cookie is Secure, HttpOnly and SameSite Strict",
    )
    alice.request(
        "/api/auth/register",
        "POST",
        {"name": "Duplicate", "email": alice.email.upper(), "password": alice.password},
        409,
    )
    check(True, "Email uniqueness is case insensitive")
    me, _ = alice.request("/api/users/me")
    check(
        "passwordHash" not in me and "totpSecret" not in me,
        "Profile response exposes no authentication secrets",
    )
    movies, _ = alice.request("/api/movies")
    check(len(movies) >= 18, "Seeded catalogue is available")
    films, _ = alice.request("/api/movies?q=INTERSTELLAR")
    check(len(films) == 1 and films[0]["id"] == "interstellar", "Title search is case insensitive")
    films, _ = alice.request("/api/movies?q=1999")
    check(any(m["id"] == "matrix" for m in films), "Search supports release year")
    films, _ = alice.request("/api/movies?genre=Animation&from=2000-01-01&to=2021-01-01")
    check(
        all("Animation" in m["genres"] and 2000 <= m["year"] <= 2021 for m in films)
        and len(films) > 0,
        "Genre and release-date filters compose",
    )
    alice.request("/api/movies?size=101", expected=400)
    alice.request("/api/movies?from=2025-01-01&to=2000-01-01", expected=400)
    check(True, "Invalid pagination and inverted date ranges are rejected")
    injection, _ = alice.request("/api/movies?q=" + quote("' MATCH (u:User) DETACH DELETE u //"))
    check(injection == [], "Cypher-like search text is treated as data")
    alice.request("/api/users/me")
    check(True, "Injection attempt left the user intact")
    movie_input = {
        "title": "Integration Film",
        "releaseDate": "2020-01-01",
        "genres": ["Drama"],
        "overview": "A film created by an integration test.",
        "director": "Test Director",
        "runtime": 90,
        "artwork": "default",
    }
    alice.request("/api/movies", "POST", movie_input, 403)
    check(True, "Normal user cannot create catalogue entries")
    token_parts = alice.token.split(".")
    payload = json.loads(base64.urlsafe_b64decode(token_parts[1] + "==="))
    payload["roles"] = ["ADMIN"]
    token_parts[1] = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    alice.request("/api/users/me", token=".".join(token_parts), expected=401)
    check(True, "Tampered JWT role is rejected")
    alice.request("/api/ratings/me/interstellar", "PUT", {"score": 6}, 400)
    alice.request("/api/ratings/me/missing", "PUT", {"score": 4}, 404)
    check(True, "Ratings enforce range and movie existence")
    alice.request(
        "/api/ratings/me/inception", "POST", {"score": 5, "review": "A maze worth revisiting."}
    )
    check(True, "Rating create works")
    alice.request("/api/ratings/me/inception", "PUT", {"score": 4, "review": "Updated note"})
    rating, _ = alice.request("/api/ratings/me/inception")
    check(
        rating["score"] == 4
        and rating["review"] == "Updated note"
        and rating["createdAt"]
        and rating["updatedAt"],
        "Rating update preserves timestamps and stores private notes",
    )

    def concurrent_rating(_):
        c = Client()
        c.token = alice.token
        c.request("/api/ratings/me/matrix", "PUT", {"score": 5})
        return True

    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        list(pool.map(concurrent_rating, range(5)))
    history, _ = alice.request("/api/users/me/ratings")
    check(
        sum(r["movie"]["id"] == "matrix" for r in history) == 1,
        "Concurrent rating upserts create one relationship; REST history delegation works",
    )
    bob = Client()
    users.append(bob)
    bob.register("bob")
    bob.request("/api/ratings/me/inception", expected=404)
    check(True, "Another account cannot read a private rating")
    bob.request("/api/ratings/me/inception", "PUT", {"score": 5})
    bob.request("/api/ratings/me/interstellar", "PUT", {"score": 5})
    recs, _ = alice.request("/api/recommendations")
    ids = [r["id"] for r in recs]
    check(
        "inception" not in ids and "matrix" not in ids,
        "Recommendations exclude movies already rated",
    )
    interstellar = next(r for r in recs if r["id"] == "interstellar")
    check(
        interstellar["reason"] == "Loved by viewers with similar taste",
        "Collaborative graph traversal uses overlapping likes",
    )
    check(
        interstellar["algorithm"] == "gds.similarity.jaccard"
        and abs(interstellar["collaborativeScore"] - 1 / 3) < 1e-9,
        "Real GDS Jaccard similarity weights the overlapping-like fixture correctly",
    )
    alice.request("/api/movies/graph", expected=403)
    check(True, "Database graph inspection requires an administrator")
    check(
        all(
            recs[i]["recommendationScore"] >= recs[i + 1]["recommendationScore"]
            for i in range(len(recs) - 1)
        ),
        "Recommendations are ranked by descending score",
    )
    recs, _ = alice.request("/api/recommendations?genre=Science%20Fiction&from=2010-01-01")
    check(
        all("Science Fiction" in m["genres"] and m["year"] >= 2010 for m in recs),
        "Recommendations honor genre and release filters",
    )
    alice.request("/api/recommendations/dismissed/interstellar", "POST", expected=204)
    recs, _ = alice.request("/api/recommendations")
    check("interstellar" not in [r["id"] for r in recs], "Dismissed picks disappear")
    hidden, _ = alice.request("/api/recommendations/dismissed")
    check(any(m["id"] == "interstellar" for m in hidden), "Dismissed picks can be read")
    alice.request("/api/recommendations/dismissed/interstellar", "PUT", expected=204)
    alice.request("/api/recommendations/dismissed/interstellar", "DELETE", expected=204)
    proxy, _ = alice.request("/api/movies/recommendations")
    check(
        any(m["id"] == "interstellar" for m in proxy),
        "Movie service delegates recommendations over REST; restore works",
    )
    alice.request("/api/users/me/watchlist/arrival", "POST", expected=204)
    alice.request("/api/users/me/watchlist/arrival", "PUT", {"note": "Friday evening"}, 204)
    watch, _ = alice.request("/api/users/me/watchlist")
    check(
        len(watch) == 1 and watch[0]["watchlistNote"] == "Friday evening",
        "Watchlist create, read, and update work",
    )
    bwatch, _ = bob.request("/api/users/me/watchlist")
    check(bwatch == [], "Watchlists are private to their owner")
    alice.request("/api/users/me/watchlist/arrival", "DELETE", expected=204)
    watch, _ = alice.request("/api/users/me/watchlist")
    check(watch == [], "Watchlist delete works")
    share, _ = alice.request(
        "/api/recommendations/shares",
        "POST",
        {"movieId": "arrival", "note": "For your next evening."},
        201,
    )
    sid = share["id"]
    received, _ = bob.request("/api/recommendations/shares/" + sid)
    check(
        received["movie"]["id"] == "arrival" and "email" not in received,
        "A friend can read a shared pick without private profile data",
    )
    bob.request("/api/recommendations/shares/" + sid, "PUT", {"note": "Hijacked"}, 404)
    bob.request("/api/recommendations/shares/" + sid, "DELETE", expected=404)
    check(True, "Only the owner can edit or revoke a share")
    alice.request("/api/recommendations/shares/" + sid, "PUT", {"note": "New note"})
    share, _ = bob.request("/api/recommendations/shares/" + sid)
    check(share["note"] == "New note", "Share notes update")
    alice.request("/api/recommendations/shares/" + sid, "DELETE", expected=204)
    bob.request("/api/recommendations/shares/" + sid, expected=404)
    check(True, "Revoked shares are no longer accessible")
    alice.request("/api/ratings/me/matrix", "DELETE", expected=204)
    alice.request("/api/ratings/me/matrix", expected=404)
    check(True, "Rating delete works")
    alice.request("/api/users/me", "PATCH", {"name": "Updated Alice"})
    profile, _ = alice.request("/api/users/me")
    check(profile["name"] == "Updated Alice", "User profile update works")
    old_cookie = alice.cookie
    alice.request("/api/auth/refresh", "POST", token="")
    rotated = alice.cookie
    check(old_cookie != rotated, "Refresh rotates the session token")
    alice.request(
        "/api/auth/refresh", "POST", expected=403, origin="https://evil.example", token=""
    )
    check(True, "Cross-origin cookie refresh is blocked")
    alice.request("/api/auth/refresh", "POST", expected=401, cookie=old_cookie, token="")
    alice.request("/api/users/me", expected=401)
    check(True, "Refresh replay revokes the account sessions and access JWTs")
    alice.login()
    alice.request("/api/auth/logout", "POST", expected=204, token="")
    alice.request("/api/users/me", expected=401)
    check(True, "Logout revokes existing access tokens")
    alice.login()
    old_token = alice.token
    new_password = "ChangedPassword!" + secrets.token_hex(8)
    alice.request(
        "/api/users/me/password",
        "PUT",
        {"password": alice.password, "newPassword": new_password, "code": ""},
        204,
    )
    alice.request("/api/users/me", token=old_token, expected=401)
    alice.request(
        "/api/auth/login",
        "POST",
        {"email": alice.email, "password": alice.password, "code": ""},
        401,
        token="",
    )
    alice.password = new_password
    alice.login()
    check(True, "Password change invalidates old credentials and sessions")
    setup, _ = alice.request(
        "/api/users/me/2fa/setup", "POST", {"password": alice.password, "code": ""}
    )
    secret = setup["secret"]
    # Leave time for the previous/current/next-step sequence instead of racing a 30-second boundary.
    phase = time.time() % 30
    if phase > 20:
        time.sleep(30 - phase + 0.2)
    step = int(time.time()) // 30
    alice.request(
        "/api/users/me/2fa/confirm",
        "POST",
        {"password": alice.password, "code": code(secret, step - 1)},
        204,
    )
    alice.request(
        "/api/auth/login",
        "POST",
        {"email": alice.email, "password": alice.password, "code": ""},
        401,
        token="",
    )
    check(True, "Enabled 2FA requires an authenticator code")
    alice.login(code(secret, step))
    check(True, "TOTP login works with a real authenticator calculation")
    alice.request(
        "/api/auth/login",
        "POST",
        {"email": alice.email, "password": alice.password, "code": code(secret, step)},
        401,
        token="",
    )
    check(True, "TOTP code replay is rejected")
    alice.request(
        "/api/users/me/2fa",
        "DELETE",
        {"password": alice.password, "code": code(secret, step + 1)},
        204,
    )
    alice.login()
    check(True, "2FA can be disabled only with password and a fresh valid code")
    env = {}
    for line in Path(".env").read_text().splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            env[key] = value
    if env.get("ADMIN_EMAIL") and env.get("ADMIN_PASSWORD"):
        admin.email = env["ADMIN_EMAIL"]
        admin.password = env["ADMIN_PASSWORD"]
        admin.login()
        created, _ = admin.request("/api/movies", "POST", movie_input, 201)
        created_movie = created["id"]
        updated = {**movie_input, "title": "Updated Integration Film", "genres": ["Animation"]}
        admin.request("/api/movies/" + created_movie, "PUT", updated)
        film, _ = alice.request("/api/movies/" + created_movie)
        check(film["title"] == updated["title"], "Administrator can create and update movies")
        check(film["genres"] == ["Animation"], "OGM reloads updated movie properties")
        snapshot, _ = admin.request("/api/movies/graph")
        links = [r for r in snapshot["relationships"] if r["source"] == "movie:" + created_movie]
        check(
            snapshot["gdsVersion"].startswith("2.13.")
            and any(r["target"] == "genre:Animation" for r in links)
            and not any(r["target"] == "genre:Drama" for r in links),
            "Live graph confirms GDS and OGM replaces old genre relationships",
        )
        check(
            not any(
                secret in json.dumps(snapshot)
                for secret in ["passwordHash", "totpSecret", "email", "review"]
            ),
            "Administrative graph projects no credentials, emails or private rating notes",
        )
        admin.request("/api/movies/" + created_movie, "DELETE", expected=204)
        alice.request("/api/movies/" + created_movie, expected=404)
        created_movie = ""
        check(True, "Administrator movie delete works")
    bob.delete()
    bob.request("/api/users/me", expected=401)
    check(True, "Deleted accounts cannot use previously issued JWTs")
    alice.delete()
    check(True, "User account delete succeeds")
finally:
    for user in users:
        if not user.deleted:
            try:
                user.login()
                user.delete()
            except Exception:
                print("Cleanup needed for test account:", user.email, flush=True)
    if created_movie:
        try:
            admin.request("/api/movies/" + created_movie, "DELETE", expected=204)
        except Exception:
            print("Cleanup needed for test movie:", created_movie, flush=True)
    Path("test-results").mkdir(exist_ok=True)
    Path("test-results/api-results.json").write_text(
        json.dumps(
            {
                "passed": len(report),
                "checks": report,
                "elapsedSeconds": round(time.time() - started, 2),
            },
            indent=2,
        )
    )
print(f"All {len(report)} API checks passed.", flush=True)
