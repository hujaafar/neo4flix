"""Bounded Neo4flix development stress test.

Creates one disposable account, runs 700 mixed requests at 4/8/16 workers, and attempts account cleanup.
Never run against production. TLS certificate and hostname verification remain enabled.
"""

import argparse
import concurrent.futures
import http.client
import json
import math
from pathlib import Path
import secrets
import socket
import ssl
import time
from collections import Counter
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="https://localhost:8443")
    parser.add_argument("--ca", required=True, help="Exported Caddy local root certificate")
    parser.add_argument(
        "--connect", help="Optional network host:port, retaining TLS hostname verification"
    )
    parser.add_argument("--out", default="audit-stress-results.json")
    args = parser.parse_args()
    url = urlsplit(args.base)
    if url.scheme != "https" or url.hostname not in ("localhost", "127.0.0.1"):
        parser.error("This harness is restricted to the local development application.")
    context = ssl.create_default_context(cafile=args.ca)
    context.load_default_certs()
    account_password = "AuditLoad!7" + secrets.token_hex(12)
    account_email = "audit-load-" + secrets.token_hex(10) + "@example.test"
    token = ""
    report = {
        "executedAtUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "scope": "One account, seed catalogue, 25% rating upserts; 4/8/16 workers. No capacity or endurance claim.",
        "stages": [],
        "cleanup": "not created",
    }

    class VerifiedConnection(http.client.HTTPSConnection):
        def connect(self):
            if not args.connect:
                return super().connect()
            host, port = args.connect.rsplit(":", 1)
            self.sock = context.wrap_socket(
                socket.create_connection((host, int(port)), timeout=self.timeout),
                server_hostname=url.hostname,
            )

    def request(method, path, body=None):
        connection = VerifiedConnection(url.hostname, url.port or 443, context=context, timeout=20)
        headers = {"Origin": args.base}
        if token:
            headers["Authorization"] = "Bearer " + token
        if body is not None:
            headers["Content-Type"] = "application/json"
        started = time.perf_counter()
        try:
            connection.request(method, path, None if body is None else json.dumps(body), headers)
            response = connection.getresponse()
            raw = response.read()
            elapsed = (time.perf_counter() - started) * 1000
            parsed = (
                json.loads(raw)
                if raw and "application/json" in response.getheader("Content-Type", "")
                else None
            )
            return response.status, parsed, elapsed
        finally:
            connection.close()

    def sample(index):
        choice = index % 4
        method, path, body = [
            ("GET", "/api/movies", None),
            ("GET", "/api/recommendations", None),
            ("GET", "/api/movies/inception", None),
            (
                "PUT",
                "/api/ratings/me/inception",
                {"score": 5, "review": "Disposable audit load fixture"},
            ),
        ][choice]
        try:
            status, data, elapsed = request(method, path, body)
            valid = status == 200 and (
                (choice == 0 and isinstance(data, list) and len(data) >= 18)
                or (
                    choice == 1
                    and isinstance(data, list)
                    and len(data) > 0
                    and all(m["id"] != "inception" for m in data)
                )
                or (choice == 2 and isinstance(data, dict) and data.get("id") == "inception")
                or (choice == 3 and isinstance(data, dict) and data.get("score") == 5)
            )
            return status, elapsed, valid
        except Exception as error:
            return type(error).__name__, None, False

    try:
        status, session, _ = request(
            "POST",
            "/api/auth/register",
            {"name": "Disposable audit load", "email": account_email, "password": account_password},
        )
        if status != 201:
            raise RuntimeError(f"Registration failed with status {status}; no load started")
        token = session["accessToken"]
        report["cleanup"] = "pending"
        status, _, _ = request("PUT", "/api/ratings/me/inception", {"score": 5})
        if status != 200:
            raise RuntimeError(f"Fixture creation failed with status {status}")
        for workers, count in [(4, 100), (8, 200), (16, 400)]:
            started = time.perf_counter()
            with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
                results = list(pool.map(sample, range(count)))
            seconds = time.perf_counter() - started
            durations = sorted(ms for _, ms, _ in results if ms is not None)
            percentile = lambda p: (
                round(durations[max(0, math.ceil(len(durations) * p) - 1)], 1)
                if durations
                else None
            )
            failed = sum(not valid for _, _, valid in results)
            stage = {
                "workers": workers,
                "requests": count,
                "elapsedSeconds": round(seconds, 2),
                "requestsPerSecond": round(count / seconds, 2),
                "p50Ms": percentile(0.5),
                "p95Ms": percentile(0.95),
                "maxMs": round(max(durations), 1) if durations else None,
                "statuses": dict(Counter(str(status) for status, _, _ in results)),
                "failedChecks": failed,
            }
            report["stages"].append(stage)
            print(json.dumps(stage), flush=True)
            if failed:
                report["stoppedReason"] = (
                    "Stopped escalation after a response or transport failure."
                )
                break
        status, history, _ = request("GET", "/api/ratings/me")
        report["ratingUniquenessPassed"] = (
            status == 200 and sum(r["movie"]["id"] == "inception" for r in history) == 1
        )
    finally:
        if token:
            try:
                status, _, _ = request(
                    "DELETE", "/api/users/me", {"password": account_password, "code": ""}
                )
                report["cleanup"] = "deleted" if status == 204 else f"failed: HTTP {status}"
            except Exception as error:
                report["cleanup"] = "failed: " + type(error).__name__
            if report["cleanup"] != "deleted":
                report["cleanupAccount"] = account_email
        Path(args.out).write_text(json.dumps(report, indent=2), encoding="utf-8")
    if (
        len(report["stages"]) != 3
        or any(s["failedChecks"] for s in report["stages"])
        or not report.get("ratingUniquenessPassed")
        or report["cleanup"] != "deleted"
    ):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
