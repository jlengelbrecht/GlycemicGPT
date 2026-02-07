/**
 * API client configuration and utilities.
 *
 * Story 1.3: First-Run Safety Disclaimer
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

/**
 * Disclaimer API types
 */
export interface DisclaimerStatusResponse {
  acknowledged: boolean;
  acknowledged_at: string | null;
  disclaimer_version: string;
}

export interface DisclaimerAcknowledgeRequest {
  session_id: string;
  checkbox_experimental: boolean;
  checkbox_not_medical_advice: boolean;
}

export interface DisclaimerAcknowledgeResponse {
  success: boolean;
  acknowledged_at: string;
  message: string;
}

export interface DisclaimerWarning {
  icon: string;
  title: string;
  text: string;
}

export interface DisclaimerCheckbox {
  id: string;
  label: string;
}

export interface DisclaimerContent {
  version: string;
  title: string;
  warnings: DisclaimerWarning[];
  checkboxes: DisclaimerCheckbox[];
  button_text: string;
}

/**
 * Check if the disclaimer has been acknowledged for a session
 */
export async function getDisclaimerStatus(
  sessionId: string
): Promise<DisclaimerStatusResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/disclaimer/status?session_id=${encodeURIComponent(sessionId)}`
  );

  if (!response.ok) {
    throw new Error(`Failed to check disclaimer status: ${response.status}`);
  }

  return response.json();
}

/**
 * Acknowledge the disclaimer
 */
export async function acknowledgeDisclaimer(
  data: DisclaimerAcknowledgeRequest
): Promise<DisclaimerAcknowledgeResponse> {
  const response = await fetch(`${API_BASE_URL}/api/disclaimer/acknowledge`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.detail || `Failed to acknowledge disclaimer: ${response.status}`);
  }

  return response.json();
}

/**
 * Get the disclaimer content to display
 */
export async function getDisclaimerContent(): Promise<DisclaimerContent> {
  const response = await fetch(`${API_BASE_URL}/api/disclaimer/content`);

  if (!response.ok) {
    throw new Error(`Failed to get disclaimer content: ${response.status}`);
  }

  return response.json();
}
