#!/bin/bash
# GlycemicGPT Local Dev Server Smoke Test
#
# Story 13.1: Local Dev Server Testing Checklist
#
# Automated smoke test that verifies backend API endpoints, user registration,
# login, settings CRUD, and frontend reachability. Run this after starting
# the dev environment with `docker compose up` (backend) and `npm run dev` (frontend).
#
# Usage:
#   ./scripts/smoke-test.sh                  # default: API=localhost:8000, Web=localhost:3000
#   ./scripts/smoke-test.sh --api-url http://localhost:8000 --web-url http://localhost:3000
#
# Exit codes:
#   0 = all checks passed
#   1 = one or more checks failed

set -euo pipefail

# --- Configuration ---
API_URL="${API_URL:-http://localhost:8000}"
WEB_URL="${WEB_URL:-http://localhost:3000}"
TEST_EMAIL="smoketest_$(date +%s)@test.local"
TEST_PASSWORD="SmokeTest123!"
COOKIE_JAR=$(mktemp)

# Parse CLI args
while [[ $# -gt 0 ]]; do
  case $1 in
    --api-url) API_URL="$2"; shift 2 ;;
    --web-url) WEB_URL="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

# --- Helpers ---
PASS=0
FAIL=0
SKIP=0

pass() { PASS=$((PASS+1)); echo "  [PASS] $1"; }
fail() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
skip() { SKIP=$((SKIP+1)); echo "  [SKIP] $1"; }

# HTTP helpers with connection timeout and error safety
api_get() {
  curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 \
    -b "$COOKIE_JAR" "$API_URL$1" 2>/dev/null || echo "000"
}

api_post() {
  curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 \
    -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST -H "Content-Type: application/json" -d "$2" "$API_URL$1" 2>/dev/null || echo "000"
}

api_put() {
  curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 \
    -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X PUT -H "Content-Type: application/json" -d "$2" "$API_URL$1" 2>/dev/null || echo "000"
}

cleanup() {
  rm -f "$COOKIE_JAR"
}
trap cleanup EXIT

# --- Start ---
echo ""
echo "============================================="
echo "  GlycemicGPT Smoke Test"
echo "============================================="
echo "  API:  $API_URL"
echo "  Web:  $WEB_URL"
echo "  User: $TEST_EMAIL"
echo "============================================="
echo ""

# -----------------------------------------------
# 1. Health & Infrastructure
# -----------------------------------------------
echo "1. Health & Infrastructure"
echo "---"

# 1a. API root
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$API_URL/" 2>/dev/null || echo "000")
if [ "$STATUS" = "200" ]; then pass "API root returns 200"; else fail "API root (got $STATUS)"; fi

# 1b. Health endpoint
BODY=$(curl -s --connect-timeout 5 --max-time 10 "$API_URL/health" 2>/dev/null || echo "{}")
if echo "$BODY" | grep -q '"healthy"'; then pass "Health endpoint: healthy"; else fail "Health endpoint: $BODY"; fi

# 1c. Liveness probe
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$API_URL/health/live" 2>/dev/null || echo "000")
if [ "$STATUS" = "200" ]; then pass "Liveness probe"; else fail "Liveness probe (got $STATUS)"; fi

# 1d. Readiness probe
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$API_URL/health/ready" 2>/dev/null || echo "000")
if [ "$STATUS" = "200" ]; then pass "Readiness probe"; else fail "Readiness probe (got $STATUS)"; fi

# 1e. Frontend reachable
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$WEB_URL" 2>/dev/null || echo "000")
if [ "$STATUS" = "200" ]; then pass "Frontend reachable"; else skip "Frontend not running at $WEB_URL (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# 2. Authentication
# -----------------------------------------------
echo "2. Authentication"
echo "---"

# 2a. Register
STATUS=$(api_post "/api/auth/register" "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")
if [ "$STATUS" = "201" ]; then pass "User registration"; else fail "Registration (got $STATUS)"; fi

# 2b. Login
STATUS=$(api_post "/api/auth/login" "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")
if [ "$STATUS" = "200" ]; then pass "User login"; else fail "Login (got $STATUS)"; fi

# 2c. Get current user (auth check)
STATUS=$(api_get "/api/auth/me")
if [ "$STATUS" = "200" ]; then pass "Get current user (authenticated)"; else fail "Get current user (got $STATUS)"; fi

# 2d. Safety disclaimer
STATUS=$(api_get "/api/disclaimer/status")
if [ "$STATUS" = "200" ]; then pass "Disclaimer status"; else fail "Disclaimer status (got $STATUS)"; fi

# 2e. Accept disclaimer
STATUS=$(api_post "/api/disclaimer/accept" "{}")
if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then pass "Accept disclaimer"; else fail "Accept disclaimer (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# 3. Settings Endpoints
# -----------------------------------------------
echo "3. Settings Endpoints"
echo "---"

# 3a. Target glucose range - GET
STATUS=$(api_get "/api/settings/target-glucose-range")
if [ "$STATUS" = "200" ]; then pass "GET target glucose range"; else fail "GET target glucose range (got $STATUS)"; fi

# 3b. Target glucose range - PUT
STATUS=$(api_put "/api/settings/target-glucose-range" '{"low_threshold":70,"high_threshold":180}')
if [ "$STATUS" = "200" ]; then pass "PUT target glucose range"; else fail "PUT target glucose range (got $STATUS)"; fi

# 3c. Brief delivery config - GET
STATUS=$(api_get "/api/settings/brief-delivery")
if [ "$STATUS" = "200" ]; then pass "GET brief delivery config"; else fail "GET brief delivery config (got $STATUS)"; fi

# 3d. Alert thresholds - GET
STATUS=$(api_get "/api/alerts/thresholds")
if [ "$STATUS" = "200" ]; then pass "GET alert thresholds"; else fail "GET alert thresholds (got $STATUS)"; fi

# 3e. Alert thresholds - PUT
STATUS=$(api_put "/api/alerts/thresholds" '{"low_warning":70,"urgent_low":55,"high_warning":180,"urgent_high":250}')
if [ "$STATUS" = "200" ]; then pass "PUT alert thresholds"; else fail "PUT alert thresholds (got $STATUS)"; fi

# 3f. Data retention config - GET
STATUS=$(api_get "/api/settings/data-retention")
if [ "$STATUS" = "200" ]; then pass "GET data retention config"; else fail "GET data retention config (got $STATUS)"; fi

# 3g. Emergency contacts - GET
STATUS=$(api_get "/api/emergency-contacts")
if [ "$STATUS" = "200" ]; then pass "GET emergency contacts"; else fail "GET emergency contacts (got $STATUS)"; fi

# 3h. Escalation config - GET
STATUS=$(api_get "/api/alerts/escalation")
if [ "$STATUS" = "200" ]; then pass "GET escalation config"; else fail "GET escalation config (got $STATUS)"; fi

# 3i. Profile - GET
STATUS=$(api_get "/api/auth/profile")
if [ "$STATUS" = "200" ]; then pass "GET user profile"; else fail "GET user profile (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# 4. AI & Insights
# -----------------------------------------------
echo "4. AI & Insights"
echo "---"

# 4a. AI provider config - GET
STATUS=$(api_get "/api/ai/provider")
if [ "$STATUS" = "200" ]; then pass "GET AI provider config"; else fail "GET AI provider config (got $STATUS)"; fi

# 4b. Insights list
STATUS=$(api_get "/api/ai/insights?limit=10")
if [ "$STATUS" = "200" ]; then pass "GET insights list"; else fail "GET insights list (got $STATUS)"; fi

# 4c. Unread count
STATUS=$(api_get "/api/ai/insights/unread-count")
if [ "$STATUS" = "200" ]; then pass "GET unread insights count"; else fail "GET unread insights count (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# 5. Integrations
# -----------------------------------------------
echo "5. Integrations"
echo "---"

# 5a. Get Dexcom status
STATUS=$(api_get "/api/integrations/dexcom/status")
if [ "$STATUS" = "200" ]; then pass "GET Dexcom integration status"; else fail "GET Dexcom status (got $STATUS)"; fi

# 5b. Get Tandem status
STATUS=$(api_get "/api/integrations/tandem/status")
if [ "$STATUS" = "200" ]; then pass "GET Tandem integration status"; else fail "GET Tandem status (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# 6. Caregiver Features
# -----------------------------------------------
echo "6. Caregiver Features"
echo "---"

# 6a. List caregivers
STATUS=$(api_get "/api/caregivers")
if [ "$STATUS" = "200" ]; then pass "GET caregivers list"; else fail "GET caregivers list (got $STATUS)"; fi

# 6b. List caregiver invitations
STATUS=$(api_get "/api/caregivers/invitations")
if [ "$STATUS" = "200" ]; then pass "GET caregiver invitations"; else fail "GET caregiver invitations (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# 7. Logout
# -----------------------------------------------
echo "7. Session Cleanup"
echo "---"

STATUS=$(api_post "/api/auth/logout" "{}")
if [ "$STATUS" = "200" ]; then pass "User logout"; else fail "Logout (got $STATUS)"; fi

# Verify logged out
STATUS=$(api_get "/api/auth/me")
if [ "$STATUS" = "401" ]; then pass "Session invalidated after logout"; else fail "Session still valid after logout (got $STATUS)"; fi

echo ""

# -----------------------------------------------
# Summary
# -----------------------------------------------
TOTAL=$((PASS+FAIL+SKIP))
echo "============================================="
echo "  Results: $PASS passed, $FAIL failed, $SKIP skipped (of $TOTAL)"
echo "============================================="

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "  Some checks failed. Review output above."
  exit 1
else
  echo ""
  echo "  All checks passed!"
  exit 0
fi
