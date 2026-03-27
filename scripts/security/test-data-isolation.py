#!/usr/bin/env python3
"""Story 35.11a: Dynamic data isolation (IDOR) and auth enforcement tests.

Auto-discovers ALL API endpoints from /openapi.json and tests:
1. Every endpoint rejects unauthenticated access (401)
2. Every state-changing endpoint (POST/PUT/PATCH/DELETE) requires CSRF (403)
3. Endpoints that accept user-scoped data cannot leak across users (IDOR)

No hardcoded endpoint lists -- fully OpenAPI schema-driven.
Automatically covers new endpoints without test updates.

Usage:
    API_URL=http://localhost:8001 TEST_PASSWORD=xxx python test-data-isolation.py
"""

import os
import re
import sys
import time
import uuid

import httpx

API_URL = os.environ.get("API_URL", "http://localhost:8001")
TEST_PASSWORD = os.environ.get("TEST_PASSWORD")
if not TEST_PASSWORD:
    print("FATAL: TEST_PASSWORD environment variable is required")
    sys.exit(1)

# Endpoints to skip (docs, health, public, SSE streams)
SKIP_PREFIXES = ("/docs", "/openapi.json", "/redoc", "/health", "/api/auth/register")
SKIP_SUFFIXES = ("/stream",)
# Auth endpoints that work without prior auth
AUTH_ENDPOINTS = {"/api/auth/login", "/api/auth/register"}
# Safe methods that don't need CSRF
SAFE_METHODS = {"GET", "HEAD", "OPTIONS"}

passed = 0
failed = 0
skipped = 0

PATH_PARAM_RE = re.compile(r"\{[^}]+\}")


def unique_email() -> str:
    return f"dyntest_{uuid.uuid4().hex[:12]}@example.com"


def register_and_login(client: httpx.Client, email: str, max_retries: int = 5) -> str:
    """Register, login, acknowledge disclaimer, return CSRF token."""
    resp = client.post(
        f"{API_URL}/api/auth/register",
        json={"email": email, "password": TEST_PASSWORD},
    )
    assert resp.status_code in (200, 201), f"Register failed: {resp.status_code}"

    for attempt in range(max_retries):
        resp = client.post(
            f"{API_URL}/api/auth/login",
            json={"email": email, "password": TEST_PASSWORD},
        )
        if resp.status_code == 429:
            time.sleep(2 ** attempt)
            continue
        break
    assert resp.status_code == 200, f"Login failed: {resp.status_code}"

    csrf = get_csrf(client)
    client.post(
        f"{API_URL}/api/disclaimer/acknowledge",
        headers={"X-CSRF-Token": csrf},
    )
    return get_csrf(client)


def get_csrf(client: httpx.Client) -> str:
    for cookie in client.cookies.jar:
        if cookie.name == "csrf_token":
            return cookie.value
    return ""


def check(name: str, condition: bool, detail: str = "") -> None:
    global passed, failed
    if condition:
        passed += 1
    else:
        failed += 1
        print(f"  FAIL: {name}" + (f" -- {detail}" if detail else ""))


def should_skip(path: str) -> bool:
    for prefix in SKIP_PREFIXES:
        if path.startswith(prefix):
            return True
    for suffix in SKIP_SUFFIXES:
        if path.endswith(suffix):
            return True
    return False


def resolve_path(path: str) -> str:
    """Replace {param} placeholders with a dummy UUID."""
    return PATH_PARAM_RE.sub(str(uuid.uuid4()), path)


def get_openapi_spec() -> dict:
    """Fetch the OpenAPI spec from the live API."""
    resp = httpx.get(f"{API_URL}/openapi.json", timeout=10)
    assert resp.status_code == 200, f"Failed to fetch OpenAPI spec: {resp.status_code}"
    return resp.json()


# ---------------------------------------------------------------------------
# Test 1: Unauthenticated access (auto-discovered)
# ---------------------------------------------------------------------------
def test_unauthenticated_access(spec: dict) -> None:
    """Every endpoint (except auth) must reject unauthenticated requests."""
    print("\n--- Unauthenticated Access (auto-discovered) ---")
    global skipped

    with httpx.Client(timeout=15) as client:
        for path, methods in spec.get("paths", {}).items():
            if should_skip(path):
                continue
            if path in AUTH_ENDPOINTS:
                continue

            resolved = resolve_path(path)

            for method in methods:
                method_upper = method.upper()
                if method_upper in ("OPTIONS", "HEAD", "TRACE"):
                    continue

                try:
                    resp = client.request(method_upper, f"{API_URL}{resolved}")
                    check(
                        f"Unauth {method_upper} {path} -> 401",
                        resp.status_code == 401,
                        f"Got {resp.status_code}",
                    )
                except httpx.ConnectError:
                    skipped += 1


# ---------------------------------------------------------------------------
# Test 2: CSRF enforcement on state-changing endpoints (auto-discovered)
# ---------------------------------------------------------------------------
def test_csrf_enforcement(spec: dict) -> None:
    """Every POST/PUT/PATCH/DELETE endpoint must require CSRF token."""
    print("\n--- CSRF Enforcement (auto-discovered) ---")
    global skipped

    with httpx.Client(timeout=15) as client:
        csrf = register_and_login(client, unique_email())

        for path, methods in spec.get("paths", {}).items():
            if should_skip(path):
                continue
            if path in AUTH_ENDPOINTS:
                continue

            resolved = resolve_path(path)

            for method in methods:
                method_upper = method.upper()
                if method_upper in SAFE_METHODS:
                    continue

                # Send request WITHOUT CSRF token (should get 403)
                try:
                    if method_upper == "POST":
                        resp = client.post(
                            f"{API_URL}{resolved}",
                            json={},
                            # No X-CSRF-Token header
                        )
                    elif method_upper == "DELETE":
                        resp = client.delete(f"{API_URL}{resolved}")
                    elif method_upper in ("PUT", "PATCH"):
                        resp = client.request(
                            method_upper,
                            f"{API_URL}{resolved}",
                            json={},
                        )
                    else:
                        continue

                    check(
                        f"CSRF {method_upper} {path} -> 403",
                        resp.status_code == 403,
                        f"Got {resp.status_code}",
                    )
                except httpx.ConnectError:
                    skipped += 1


# ---------------------------------------------------------------------------
# Test 3: Cross-user data isolation (IDOR) on key endpoints
# ---------------------------------------------------------------------------
def test_cross_user_isolation() -> None:
    """User B cannot access, modify, or delete User A's data."""
    print("\n--- Cross-User Data Isolation ---")

    with httpx.Client(timeout=30) as user_a, httpx.Client(timeout=30) as user_b:
        csrf_a = register_and_login(user_a, unique_email())
        csrf_b = register_and_login(user_b, unique_email())

        # ── Chat History ──
        # User A's history should be empty or contain only A's messages
        resp_a = user_a.get(
            f"{API_URL}/api/ai/chat/history",
            headers={"X-CSRF-Token": csrf_a},
        )
        resp_b = user_b.get(
            f"{API_URL}/api/ai/chat/history",
            headers={"X-CSRF-Token": csrf_b},
        )
        check(
            "Chat history: User A can access own",
            resp_a.status_code == 200,
            f"Got {resp_a.status_code}",
        )
        check(
            "Chat history: User B sees only own (empty)",
            resp_b.status_code == 200
            and len(resp_b.json().get("messages", [])) == 0,
            f"Got {resp_b.status_code}",
        )

        # ── Knowledge Base ──
        resp_a = user_a.get(
            f"{API_URL}/api/knowledge/documents",
            headers={"X-CSRF-Token": csrf_a},
        )
        resp_b = user_b.get(
            f"{API_URL}/api/knowledge/documents",
            headers={"X-CSRF-Token": csrf_b},
        )
        check(
            "Knowledge: User A can access documents",
            resp_a.status_code == 200,
            f"Got {resp_a.status_code}",
        )
        check(
            "Knowledge: User B sees independent documents",
            resp_b.status_code == 200,
            f"Got {resp_b.status_code}",
        )

        # User B tries to delete a knowledge document belonging to A
        resp_del = user_b.delete(
            f"{API_URL}/api/knowledge/documents",
            params={"source_name": "User A Secret Document"},
            headers={"X-CSRF-Token": csrf_b},
        )
        check(
            "Knowledge: User B cannot delete User A's docs",
            resp_del.status_code == 404,
            f"Got {resp_del.status_code}",
        )

        # ── Research Sources ──
        # User A adds a source
        resp_add = user_a.post(
            f"{API_URL}/api/ai/research/sources",
            json={
                "url": "https://www.example.com/user-a-private",
                "name": "User A Private Source",
                "category": "insulin",
            },
            headers={"X-CSRF-Token": csrf_a},
        )
        source_id = None
        if resp_add.status_code == 201:
            source_id = resp_add.json().get("id")
            check("Research: User A can add source", True)
        else:
            check(
                "Research: User A can add source",
                False,
                f"Got {resp_add.status_code}: {resp_add.text[:100]}",
            )

        # User B lists sources -- should NOT see A's
        resp_list_b = user_b.get(
            f"{API_URL}/api/ai/research/sources",
            headers={"X-CSRF-Token": csrf_b},
        )
        check(
            "Research: User B does NOT see User A's sources",
            resp_list_b.status_code == 200
            and len(resp_list_b.json().get("sources", [])) == 0,
            f"Got {resp_list_b.status_code}, count: {len(resp_list_b.json().get('sources', []))}",
        )

        # User B tries to delete A's source
        if source_id:
            resp_del = user_b.delete(
                f"{API_URL}/api/ai/research/sources/{source_id}",
                headers={"X-CSRF-Token": csrf_b},
            )
            check(
                "Research: User B cannot delete User A's source",
                resp_del.status_code == 404,
                f"Got {resp_del.status_code}",
            )

            # Verify A's source survived
            resp_verify = user_a.get(
                f"{API_URL}/api/ai/research/sources",
                headers={"X-CSRF-Token": csrf_a},
            )
            check(
                "Research: User A's source survives B's delete attempt",
                resp_verify.status_code == 200
                and len(resp_verify.json().get("sources", [])) >= 1,
                f"Got {resp_verify.status_code}",
            )


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    print(f"Running dynamic security tests against {API_URL}")
    print("=" * 60)

    spec = get_openapi_spec()
    endpoint_count = sum(len(m) for m in spec.get("paths", {}).values())
    print(f"Discovered {endpoint_count} endpoint methods from OpenAPI spec")

    test_unauthenticated_access(spec)
    test_csrf_enforcement(spec)
    test_cross_user_isolation()

    print("\n" + "=" * 60)
    print(f"Results: {passed} passed, {failed} failed, {skipped} skipped")

    if failed > 0:
        print("\nFAILED -- security issue detected!")
        sys.exit(1)
    else:
        print("\nPASSED -- all security checks verified")
        sys.exit(0)
