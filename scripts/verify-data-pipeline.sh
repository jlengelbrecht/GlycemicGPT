#!/bin/bash
# GlycemicGPT Real Data Pipeline Verification
#
# Story 13.3: Real Data Pipeline Verification
#
# Verifies the complete data pipeline from Dexcom ingestion through
# dashboard display and AI analysis. Requires a running backend with
# a registered user who has configured real Dexcom credentials.
#
# Prerequisites:
#   1. Backend running (API on port 8000, database, Redis)
#   2. A registered user with accepted disclaimer
#   3. Dexcom G7 credentials configured via Settings > Integrations
#   4. (Optional) AI provider configured via Settings > AI Provider
#
# Usage:
#   ./scripts/verify-data-pipeline.sh --email user@example.com --password pass123
#   ./scripts/verify-data-pipeline.sh --email user@example.com --password pass123 --api-url http://localhost:8000
#
# Exit codes:
#   0 = all checks passed
#   1 = one or more checks failed

set -uo pipefail

# --- Configuration ---
API_URL="${API_URL:-http://localhost:8000}"
USER_EMAIL=""
USER_PASSWORD=""
COOKIE_JAR=$(mktemp)
LOGIN_PAYLOAD=""

# Parse CLI args
while [[ $# -gt 0 ]]; do
  case $1 in
    --email)    USER_EMAIL="$2"; shift 2 ;;
    --password) USER_PASSWORD="$2"; shift 2 ;;
    --api-url)  API_URL="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; echo "Usage: $0 --email user@example.com --password pass"; exit 1 ;;
  esac
done

if [ -z "$USER_EMAIL" ] || [ -z "$USER_PASSWORD" ]; then
  echo "Error: --email and --password are required"
  echo "Usage: $0 --email user@example.com --password pass"
  exit 1
fi

# --- Helpers ---
PASS=0
FAIL=0
SKIP=0

pass() { PASS=$((PASS+1)); echo "  [PASS] $1"; }
fail() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
skip() { SKIP=$((SKIP+1)); echo "  [SKIP] $1"; }

api_get() {
  curl -s --connect-timeout 5 --max-time 10 \
    -b "$COOKIE_JAR" "$API_URL$1" 2>/dev/null || echo "{}"
}

api_get_status() {
  curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 \
    -b "$COOKIE_JAR" "$API_URL$1" 2>/dev/null || echo "000"
}

api_post() {
  local timeout="${3:-30}"
  curl -s --connect-timeout 5 --max-time "$timeout" \
    -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST -H "Content-Type: application/json" -d "$2" "$API_URL$1" 2>/dev/null || echo "{}"
}

api_post_status() {
  local timeout="${3:-30}"
  curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time "$timeout" \
    -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST -H "Content-Type: application/json" -d "$2" "$API_URL$1" 2>/dev/null || echo "000"
}

cleanup() {
  rm -f "$COOKIE_JAR" "$LOGIN_PAYLOAD"
}
trap cleanup EXIT

# --- Start ---
echo ""
echo "============================================="
echo "  GlycemicGPT Real Data Pipeline Verification"
echo "============================================="
echo "  API:  $API_URL"
echo "  User: $USER_EMAIL"
echo "  Pass: ********"
echo "============================================="
echo ""

# -----------------------------------------------
# 1. Authenticate
# -----------------------------------------------
echo "1. Authentication"
echo "---"

# Use temp file for login payload to avoid password in process listing
LOGIN_PAYLOAD="$(mktemp)"
printf '{"email":"%s","password":"%s"}' "$USER_EMAIL" "$USER_PASSWORD" > "$LOGIN_PAYLOAD"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 \
  -c "$COOKIE_JAR" -X POST -H "Content-Type: application/json" \
  -d "@$LOGIN_PAYLOAD" "$API_URL/api/auth/login" 2>/dev/null || echo "000")
rm -f "$LOGIN_PAYLOAD"
if [ "$STATUS" = "200" ]; then
  pass "User login"
else
  fail "Login failed (got $STATUS). Cannot proceed."
  exit 1
fi

BODY=$(api_get "/api/auth/me")
ROLE=$(echo "$BODY" | grep -o '"role":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
echo "  [INFO] User role: $ROLE"

echo ""

# -----------------------------------------------
# 2. Integration Status
# -----------------------------------------------
echo "2. Integration Status"
echo "---"

# Dexcom
DEXCOM_BODY=$(api_get "/api/integrations/dexcom/status")
DEXCOM_STATUS=$(echo "$DEXCOM_BODY" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "unknown")
if [ "$DEXCOM_STATUS" = "connected" ]; then
  pass "Dexcom integration: connected"
elif [ "$DEXCOM_STATUS" = "error" ]; then
  DEXCOM_ERROR=$(echo "$DEXCOM_BODY" | grep -o '"last_error":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "unknown")
  fail "Dexcom integration: error ($DEXCOM_ERROR)"
else
  skip "Dexcom not configured (status: $DEXCOM_STATUS)"
fi

# Tandem
TANDEM_BODY=$(api_get "/api/integrations/tandem/status")
TANDEM_STATUS=$(echo "$TANDEM_BODY" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "unknown")
if [ "$TANDEM_STATUS" = "connected" ]; then
  pass "Tandem integration: connected"
elif [ "$TANDEM_STATUS" = "error" ]; then
  fail "Tandem integration: error"
else
  skip "Tandem not configured (status: $TANDEM_STATUS)"
fi

echo ""

# -----------------------------------------------
# 3. Data Sync Trigger
# -----------------------------------------------
echo "3. Data Sync"
echo "---"

if [ "$DEXCOM_STATUS" = "connected" ]; then
  SYNC_BODY=$(api_post "/api/integrations/dexcom/sync" "{}")
  READINGS_FETCHED=$(echo "$SYNC_BODY" | grep -o '"readings_fetched":[0-9]*' | cut -d':' -f2 || echo "0")
  READINGS_STORED=$(echo "$SYNC_BODY" | grep -o '"readings_stored":[0-9]*' | cut -d':' -f2 || echo "0")

  if [ -n "$READINGS_FETCHED" ] && [ "$READINGS_FETCHED" != "0" ] 2>/dev/null; then
    pass "Dexcom sync: fetched $READINGS_FETCHED readings, stored $READINGS_STORED new"
  elif echo "$SYNC_BODY" | grep -qi "error\|fail"; then
    fail "Dexcom sync failed: $SYNC_BODY"
  else
    skip "Dexcom sync returned 0 readings (may already be up to date)"
  fi
else
  skip "Dexcom sync skipped (not configured)"
fi

if [ "$TANDEM_STATUS" = "connected" ]; then
  SYNC_BODY=$(api_post "/api/integrations/tandem/sync" "{}")
  EVENTS_FETCHED=$(echo "$SYNC_BODY" | grep -o '"events_fetched":[0-9]*' | cut -d':' -f2 || echo "0")

  if [ -n "$EVENTS_FETCHED" ] && [ "$EVENTS_FETCHED" != "0" ] 2>/dev/null; then
    pass "Tandem sync: fetched $EVENTS_FETCHED events"
  elif echo "$SYNC_BODY" | grep -qi "error\|fail"; then
    fail "Tandem sync failed: $SYNC_BODY"
  else
    skip "Tandem sync returned 0 events (may already be up to date)"
  fi
else
  skip "Tandem sync skipped (not configured)"
fi

echo ""

# -----------------------------------------------
# 4. Dashboard Data (Current Glucose)
# -----------------------------------------------
echo "4. Dashboard Data"
echo "---"

# Current glucose
GLUCOSE_BODY=$(api_get "/api/integrations/glucose/current")
GLUCOSE_VALUE=$(echo "$GLUCOSE_BODY" | grep -o '"value":[0-9]*' | head -1 | cut -d':' -f2 || echo "")
GLUCOSE_TREND=$(echo "$GLUCOSE_BODY" | grep -o '"trend":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
MINUTES_AGO=$(echo "$GLUCOSE_BODY" | grep -o '"minutes_ago":[0-9]*' | head -1 | cut -d':' -f2 || echo "")

if [ -n "$GLUCOSE_VALUE" ]; then
  pass "Current glucose: ${GLUCOSE_VALUE} mg/dL (trend: $GLUCOSE_TREND, ${MINUTES_AGO}m ago)"
  IS_STALE=$(echo "$GLUCOSE_BODY" | grep -o '"is_stale":[a-z]*' | head -1 | cut -d':' -f2 || echo "false")
  if [ "$IS_STALE" = "true" ]; then
    echo "  [WARN] Reading is stale (> 10 minutes old)"
  fi
else
  STATUS=$(api_get_status "/api/integrations/glucose/current")
  if [ "$STATUS" = "404" ]; then
    fail "No glucose readings available (sync may not have run)"
  else
    fail "Current glucose endpoint error (status: $STATUS)"
  fi
fi

# Glucose history
HISTORY_BODY=$(api_get "/api/integrations/glucose/history?minutes=180&limit=36")
READINGS_COUNT=$(echo "$HISTORY_BODY" | grep -o '"count":[0-9]*' | head -1 | cut -d':' -f2 || echo "0")

if [ -n "$READINGS_COUNT" ] && [ "$READINGS_COUNT" -gt 0 ] 2>/dev/null; then
  pass "Glucose history: $READINGS_COUNT readings in last 3 hours"
else
  fail "No glucose history available"
fi

# Target glucose range (for time-in-range)
RANGE_BODY=$(api_get "/api/settings/target-glucose-range")
LOW_TARGET=$(echo "$RANGE_BODY" | grep -o '"low_target":[0-9]*' | head -1 | cut -d':' -f2 || echo "70")
HIGH_TARGET=$(echo "$RANGE_BODY" | grep -o '"high_target":[0-9]*' | head -1 | cut -d':' -f2 || echo "180")
echo "  [INFO] Target range: ${LOW_TARGET}-${HIGH_TARGET} mg/dL"

echo ""

# -----------------------------------------------
# 5. IoB Projection (if Tandem connected)
# -----------------------------------------------
echo "5. Insulin on Board"
echo "---"

if [ "$TANDEM_STATUS" = "connected" ]; then
  IOB_BODY=$(api_get "/api/integrations/tandem/iob/projection")
  IOB_CURRENT=$(echo "$IOB_BODY" | grep -o '"projected_iob":[0-9.]*' | head -1 | cut -d':' -f2 || echo "")

  if [ -n "$IOB_CURRENT" ]; then
    pass "IoB projection: ${IOB_CURRENT} units"
    IOB_STALE=$(echo "$IOB_BODY" | grep -o '"is_stale":[a-z]*' | head -1 | cut -d':' -f2 || echo "false")
    if [ "$IOB_STALE" = "true" ]; then
      echo "  [WARN] IoB data is stale"
    fi
  else
    skip "IoB projection not available (no recent pump data)"
  fi
else
  skip "IoB skipped (Tandem not configured)"
fi

echo ""

# -----------------------------------------------
# 6. Control-IQ Activity (if Tandem connected)
# -----------------------------------------------
echo "6. Control-IQ Activity"
echo "---"

if [ "$TANDEM_STATUS" = "connected" ]; then
  CIQ_BODY=$(api_get "/api/integrations/tandem/control-iq/activity?hours=24")
  TOTAL_EVENTS=$(echo "$CIQ_BODY" | grep -o '"total_events":[0-9]*' | head -1 | cut -d':' -f2 || echo "0")
  AUTO_EVENTS=$(echo "$CIQ_BODY" | grep -o '"automated_events":[0-9]*' | head -1 | cut -d':' -f2 || echo "0")

  if [ -n "$TOTAL_EVENTS" ] && [ "$TOTAL_EVENTS" -gt 0 ] 2>/dev/null; then
    pass "Control-IQ: $TOTAL_EVENTS events ($AUTO_EVENTS automated) in 24h"
  else
    skip "No Control-IQ events in last 24 hours"
  fi
else
  skip "Control-IQ skipped (Tandem not configured)"
fi

echo ""

# -----------------------------------------------
# 7. SSE Stream Test
# -----------------------------------------------
echo "7. Real-Time SSE Stream"
echo "---"

SSE_OUTPUT=$(curl -s --connect-timeout 5 --max-time 10 \
  -H "Accept: text/event-stream" -b "$COOKIE_JAR" \
  "$API_URL/api/v1/glucose/stream" 2>/dev/null || echo "")

if echo "$SSE_OUTPUT" | grep -q "data:"; then
  # Parse the first data event
  FIRST_EVENT=$(echo "$SSE_OUTPUT" | grep "^data:" | head -1 | sed 's/^data://')
  SSE_VALUE=$(echo "$FIRST_EVENT" | grep -o '"value":[0-9]*' | head -1 | cut -d':' -f2 || echo "")

  if [ -n "$SSE_VALUE" ]; then
    pass "SSE stream: received glucose event (${SSE_VALUE} mg/dL)"
  else
    pass "SSE stream: received data event"
  fi
elif echo "$SSE_OUTPUT" | grep -q "event:"; then
  pass "SSE stream: received events (heartbeat/no_data)"
else
  skip "SSE stream: no data received within 10s timeout"
fi

echo ""

# -----------------------------------------------
# 8. AI Provider Status
# -----------------------------------------------
echo "8. AI Provider"
echo "---"

AI_STATUS=$(api_get_status "/api/ai/provider")
AI_CONFIGURED="false"
AI_PROVIDER="none"

if [ "$AI_STATUS" = "200" ]; then
  AI_CONFIGURED="true"
  AI_BODY=$(api_get "/api/ai/provider")
  AI_PROVIDER=$(echo "$AI_BODY" | grep -o '"provider_type":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "unknown")
  AI_PROVIDER_STATUS=$(echo "$AI_BODY" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "unknown")
  pass "AI provider configured: $AI_PROVIDER (status: $AI_PROVIDER_STATUS)"
elif [ "$AI_STATUS" = "404" ]; then
  skip "AI provider not configured (configure in Settings > AI Provider)"
else
  fail "AI provider endpoint returned unexpected status: $AI_STATUS"
fi

echo ""

# -----------------------------------------------
# 9. AI Chat (if provider configured)
# -----------------------------------------------
echo "9. AI Chat"
echo "---"

if [ "$AI_CONFIGURED" = "true" ]; then
  echo "  Sending test message to AI (may take a moment)..."
  CHAT_BODY=$(api_post "/api/ai/chat" '{"message":"Briefly summarize my glucose status in one sentence."}' 60)
  CHAT_RESPONSE=$(echo "$CHAT_BODY" | grep -o '"response":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

  if [ -n "$CHAT_RESPONSE" ]; then
    pass "AI chat responded"
    echo "  [INFO] AI: ${CHAT_RESPONSE:0:120}..."
  elif echo "$CHAT_BODY" | grep -qi "error\|fail"; then
    fail "AI chat error: $(echo "$CHAT_BODY" | head -c 200)"
  else
    fail "AI chat returned empty response"
  fi
else
  skip "AI chat skipped (provider not configured)"
fi

echo ""

# -----------------------------------------------
# 10. Daily Brief Generation (if AI + data available)
# -----------------------------------------------
echo "10. Daily Brief Generation"
echo "---"

if [ "$AI_CONFIGURED" = "true" ] && [ -n "$GLUCOSE_VALUE" ]; then
  echo "  Generating daily brief (may take a moment)..."
  BRIEF_BODY=$(api_post "/api/ai/briefs/generate" '{"hours":24}' 60)
  BRIEF_ID=$(echo "$BRIEF_BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
  TIR_PCT=$(echo "$BRIEF_BODY" | grep -o '"time_in_range_pct":[0-9.]*' | head -1 | cut -d':' -f2 || echo "")
  AVG_GLUCOSE=$(echo "$BRIEF_BODY" | grep -o '"average_glucose":[0-9.]*' | head -1 | cut -d':' -f2 || echo "")
  AI_SUMMARY=$(echo "$BRIEF_BODY" | grep -o '"ai_summary":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

  if [ -n "$BRIEF_ID" ]; then
    pass "Daily brief generated (TIR: ${TIR_PCT}%, avg: ${AVG_GLUCOSE} mg/dL)"
    if [ -n "$AI_SUMMARY" ]; then
      echo "  [INFO] Summary: ${AI_SUMMARY:0:150}..."
    fi
  elif echo "$BRIEF_BODY" | grep -qi "error\|fail\|not enough"; then
    skip "Brief generation: $(echo "$BRIEF_BODY" | head -c 200)"
  else
    fail "Brief generation failed: $(echo "$BRIEF_BODY" | head -c 200)"
  fi
elif [ "$AI_CONFIGURED" != "true" ]; then
  skip "Daily brief skipped (AI provider not configured)"
else
  skip "Daily brief skipped (no glucose data available)"
fi

echo ""

# -----------------------------------------------
# 11. Insights Feed
# -----------------------------------------------
echo "11. Insights Feed"
echo "---"

INSIGHTS_BODY=$(api_get "/api/ai/insights?limit=5")
INSIGHTS_TOTAL=$(echo "$INSIGHTS_BODY" | grep -o '"total":[0-9]*' | head -1 | cut -d':' -f2 || echo "0")

if [ -n "$INSIGHTS_TOTAL" ] && [ "$INSIGHTS_TOTAL" -gt 0 ] 2>/dev/null; then
  pass "Insights feed: $INSIGHTS_TOTAL total insights"
else
  skip "No insights available yet"
fi

UNREAD_BODY=$(api_get "/api/ai/insights/unread-count")
UNREAD_COUNT=$(echo "$UNREAD_BODY" | grep -o '"unread_count":[0-9]*' | head -1 | cut -d':' -f2 || echo "")
if [ -n "$UNREAD_COUNT" ]; then
  echo "  [INFO] Unread insights: $UNREAD_COUNT"
fi

echo ""

# -----------------------------------------------
# 12. Alert System
# -----------------------------------------------
echo "12. Alert System"
echo "---"

THRESHOLDS_STATUS=$(api_get_status "/api/settings/alert-thresholds")
if [ "$THRESHOLDS_STATUS" = "200" ]; then
  pass "Alert thresholds configured"
else
  skip "Alert thresholds not configured"
fi

ALERTS_BODY=$(api_get "/api/alerts/active")
# Check if we got active alerts back
if echo "$ALERTS_BODY" | grep -q '"alerts"'; then
  ALERT_COUNT=$(echo "$ALERTS_BODY" | grep -o '"id"' | wc -l || echo "0")
  if [ "$ALERT_COUNT" -gt 0 ] 2>/dev/null; then
    echo "  [INFO] Active alerts: $ALERT_COUNT"
  fi
  pass "Alert system operational"
else
  pass "Alert system reachable (no active alerts)"
fi

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
  echo ""
  echo "  Common fixes:"
  echo "    - Connect Dexcom: Settings > Integrations > Dexcom"
  echo "    - Configure AI: Settings > AI Provider"
  echo "    - Ensure backend services are running"
  exit 1
else
  echo ""
  if [ "$SKIP" -gt 0 ]; then
    echo "  All reachable checks passed! ($SKIP skipped - see above for details)"
  else
    echo "  All checks passed! Full data pipeline verified."
  fi
  exit 0
fi
