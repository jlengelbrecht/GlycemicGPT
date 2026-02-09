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

/**
 * AI Insights API types (Story 5.7)
 */
export interface InsightSummary {
  id: string;
  analysis_type: "daily_brief" | "meal_analysis" | "correction_analysis";
  title: string;
  content: string;
  created_at: string;
  status: "pending" | "acknowledged" | "dismissed";
}

export interface InsightsListResponse {
  insights: InsightSummary[];
  total: number;
}

export interface SuggestionResponseResponse {
  id: string;
  analysis_type: string;
  analysis_id: string;
  response: string;
  reason: string | null;
  created_at: string;
}

export interface ModelInfo {
  model: string;
  provider: string;
  input_tokens: number;
  output_tokens: number;
}

export interface SafetyInfo {
  status: string;
  has_dangerous_content: boolean;
  flagged_items: Record<string, unknown>[];
  validated_at: string;
}

export interface UserResponseInfo {
  response: string;
  reason: string | null;
  responded_at: string;
}

export interface InsightDetail {
  id: string;
  analysis_type: "daily_brief" | "meal_analysis" | "correction_analysis";
  title: string;
  content: string;
  created_at: string;
  status: "pending" | "acknowledged" | "dismissed";
  period_start: string;
  period_end: string;
  data_context: Record<string, unknown>;
  model_info: ModelInfo;
  safety: SafetyInfo | null;
  user_response: UserResponseInfo | null;
}

/**
 * Fetch detailed view of a single AI insight
 */
export async function getInsightDetail(
  analysisType: string,
  analysisId: string
): Promise<InsightDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/ai/insights/${encodeURIComponent(analysisType)}/${encodeURIComponent(analysisId)}`,
    {
      credentials: "include",
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.detail || `Failed to fetch insight detail: ${response.status}`);
  }

  return response.json();
}

/**
 * Fetch AI insights for the current user
 */
export async function getInsights(
  limit: number = 10
): Promise<InsightsListResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/ai/insights?limit=${limit}`,
    {
      credentials: "include",
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to fetch insights: ${response.status}`);
  }

  return response.json();
}

/**
 * Record a response to an AI insight
 */
export async function respondToInsight(
  analysisType: string,
  analysisId: string,
  response: "acknowledged" | "dismissed",
  reason?: string
): Promise<SuggestionResponseResponse> {
  const res = await fetch(
    `${API_BASE_URL}/api/ai/insights/${analysisType}/${analysisId}/respond`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ response, reason }),
    }
  );

  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.detail || `Failed to respond to insight: ${res.status}`);
  }

  return res.json();
}

/**
 * Alert Threshold API types (Story 6.1)
 */
export interface AlertThresholdResponse {
  id: string;
  low_warning: number;
  urgent_low: number;
  high_warning: number;
  urgent_high: number;
  iob_warning: number;
  updated_at: string;
}

export interface AlertThresholdUpdate {
  low_warning?: number;
  urgent_low?: number;
  high_warning?: number;
  urgent_high?: number;
  iob_warning?: number;
}

/**
 * Fetch current alert thresholds
 */
export async function getAlertThresholds(): Promise<AlertThresholdResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/alert-thresholds`,
    {
      credentials: "include",
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.detail || `Failed to fetch thresholds: ${response.status}`);
  }

  return response.json();
}

/**
 * Update alert thresholds
 */
export async function updateAlertThresholds(
  updates: AlertThresholdUpdate
): Promise<AlertThresholdResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/alert-thresholds`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(updates),
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.detail || `Failed to update thresholds: ${response.status}`);
  }

  return response.json();
}

/**
 * Predictive Alert API types (Story 6.2)
 */
export interface PredictiveAlert {
  id: string;
  alert_type: string;
  severity: string;
  current_value: number;
  predicted_value: number | null;
  prediction_minutes: number | null;
  iob_value: number | null;
  message: string;
  trend_rate: number | null;
  source: string;
  acknowledged: boolean;
  acknowledged_at: string | null;
  created_at: string;
  expires_at: string;
}

export interface ActiveAlertsResponse {
  alerts: PredictiveAlert[];
  count: number;
}

export interface AlertAcknowledgeResponse {
  id: string;
  acknowledged: boolean;
  acknowledged_at: string | null;
}

/**
 * Fetch active (unacknowledged, non-expired) alerts
 */
export async function getActiveAlerts(): Promise<ActiveAlertsResponse> {
  const response = await fetch(`${API_BASE_URL}/api/alerts/active`, {
    credentials: "include",
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || `Failed to fetch alerts: ${response.status}`
    );
  }

  return response.json();
}

/**
 * Acknowledge an alert by ID
 */
export async function acknowledgeAlert(
  alertId: string
): Promise<AlertAcknowledgeResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/alerts/${encodeURIComponent(alertId)}/acknowledge`,
    {
      method: "PATCH",
      credentials: "include",
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || `Failed to acknowledge alert: ${response.status}`
    );
  }

  return response.json();
}

/**
 * Emergency Contact API types (Story 6.5)
 */
export interface EmergencyContact {
  id: string;
  name: string;
  telegram_username: string;
  priority: "primary" | "secondary";
  position: number;
  created_at: string;
  updated_at: string;
}

export interface EmergencyContactListResponse {
  contacts: EmergencyContact[];
  count: number;
}

export interface EmergencyContactCreate {
  name: string;
  telegram_username: string;
  priority: "primary" | "secondary";
}

export interface EmergencyContactUpdate {
  name?: string;
  telegram_username?: string;
  priority?: "primary" | "secondary";
}

/**
 * Fetch all emergency contacts
 */
export async function getEmergencyContacts(): Promise<EmergencyContactListResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/emergency-contacts`,
    { credentials: "include" }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || `Failed to fetch emergency contacts: ${response.status}`
    );
  }

  return response.json();
}

/**
 * Create a new emergency contact
 */
export async function createEmergencyContact(
  data: EmergencyContactCreate
): Promise<EmergencyContact> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/emergency-contacts`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(data),
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || `Failed to create emergency contact: ${response.status}`
    );
  }

  return response.json();
}

/**
 * Update an existing emergency contact
 */
export async function updateEmergencyContact(
  contactId: string,
  data: EmergencyContactUpdate
): Promise<EmergencyContact> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/emergency-contacts/${encodeURIComponent(contactId)}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(data),
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || `Failed to update emergency contact: ${response.status}`
    );
  }

  return response.json();
}

/**
 * Delete an emergency contact
 */
export async function deleteEmergencyContact(
  contactId: string
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/emergency-contacts/${encodeURIComponent(contactId)}`,
    {
      method: "DELETE",
      credentials: "include",
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || `Failed to delete emergency contact: ${response.status}`
    );
  }
}

/**
 * Escalation Config API types (Story 6.6)
 */
export interface EscalationConfigResponse {
  id: string;
  reminder_delay_minutes: number;
  primary_contact_delay_minutes: number;
  all_contacts_delay_minutes: number;
  updated_at: string;
}

export interface EscalationConfigUpdate {
  reminder_delay_minutes?: number;
  primary_contact_delay_minutes?: number;
  all_contacts_delay_minutes?: number;
}

/**
 * Fetch escalation timing configuration
 */
export async function getEscalationConfig(): Promise<EscalationConfigResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/escalation-config`,
    { credentials: "include" }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail ||
        `Failed to fetch escalation config: ${response.status}`
    );
  }

  return response.json();
}

/**
 * Update escalation timing configuration
 */
export async function updateEscalationConfig(
  data: EscalationConfigUpdate
): Promise<EscalationConfigResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/settings/escalation-config`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(data),
    }
  );

  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail ||
        `Failed to update escalation config: ${response.status}`
    );
  }

  return response.json();
}
