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
