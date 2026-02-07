/**
 * Session management utilities.
 *
 * Story 1.3: First-Run Safety Disclaimer
 * Manages a persistent session ID for tracking disclaimer acknowledgment
 * before user authentication is implemented.
 */

const SESSION_ID_KEY = "glycemicgpt_session_id";

/**
 * Generate a UUID v4
 */
function generateUUID(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Get or create a session ID.
 * Returns null during SSR.
 */
export function getSessionId(): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  let sessionId = localStorage.getItem(SESSION_ID_KEY);

  if (!sessionId) {
    sessionId = generateUUID();
    localStorage.setItem(SESSION_ID_KEY, sessionId);
  }

  return sessionId;
}

/**
 * Clear the session ID (useful for testing)
 */
export function clearSessionId(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(SESSION_ID_KEY);
  }
}
