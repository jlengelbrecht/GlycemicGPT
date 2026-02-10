"use client";

/**
 * Story 8.3: Caregiver Dashboard View
 *
 * Read-only dashboard for caregivers to view their linked patient's
 * glucose status. Data is filtered by permissions set by the patient.
 * Auto-refreshes every 60 seconds via REST polling.
 */

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  Clock,
  Syringe,
  Loader2,
  AlertTriangle,
  Eye,
  Lock,
  MessageSquare,
  RefreshCw,
  Send,
  Users,
} from "lucide-react";
import clsx from "clsx";
import {
  listLinkedPatients,
  getCaregiverPatientStatus,
  sendCaregiverChat,
  type LinkedPatient,
  type CaregiverPatientStatus,
  type CaregiverChatResponse,
} from "@/lib/api";
import { useUserContext } from "@/providers";

const REFRESH_INTERVAL_MS = 60_000;

function getTrendArrow(trend: string): string {
  const arrows: Record<string, string> = {
    RisingQuickly: "\u2191\u2191",
    Rising: "\u2191",
    SlightlyRising: "\u2197",
    Flat: "\u2192",
    SlightlyFalling: "\u2198",
    Falling: "\u2193",
    FallingQuickly: "\u2193\u2193",
  };
  return arrows[trend] || "?";
}

function getGlucoseColor(value: number): string {
  if (value < 70) return "text-red-400";
  if (value < 80) return "text-yellow-400";
  if (value <= 180) return "text-green-400";
  if (value <= 250) return "text-yellow-400";
  return "text-red-400";
}

export default function CaregiverDashboardPage() {
  const router = useRouter();
  const { user, isLoading: isUserLoading } = useUserContext();
  const [patients, setPatients] = useState<LinkedPatient[]>([]);
  const [selectedPatientId, setSelectedPatientId] = useState<string | null>(
    null
  );
  const [status, setStatus] = useState<CaregiverPatientStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<Date | null>(null);

  // Story 8.4: AI chat state
  const [chatMessage, setChatMessage] = useState("");
  const [chatResponse, setChatResponse] =
    useState<CaregiverChatResponse | null>(null);
  const [isChatLoading, setIsChatLoading] = useState(false);
  const [chatError, setChatError] = useState<string | null>(null);

  // Redirect non-caregivers away from this page
  useEffect(() => {
    if (!isUserLoading && user && user.role !== "caregiver") {
      router.replace("/dashboard");
    }
  }, [user, isUserLoading, router]);

  // Load linked patients on mount
  useEffect(() => {
    async function loadPatients() {
      try {
        const data = await listLinkedPatients();
        setPatients(data.patients);
        if (data.patients.length > 0) {
          setSelectedPatientId(data.patients[0].patient_id);
        }
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load patients"
        );
      } finally {
        setIsLoading(false);
      }
    }
    loadPatients();
  }, []);

  // Fetch patient status
  const fetchStatus = useCallback(
    async (showRefreshing = false) => {
      if (!selectedPatientId) return;

      if (showRefreshing) setIsRefreshing(true);
      try {
        const data = await getCaregiverPatientStatus(selectedPatientId);
        setStatus(data);
        setError(null);
        setLastRefresh(new Date());
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to fetch status"
        );
      } finally {
        setIsRefreshing(false);
      }
    },
    [selectedPatientId]
  );

  // Story 8.4: AI chat handler
  const handleChatSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!selectedPatientId || !chatMessage.trim() || isChatLoading) return;

      setIsChatLoading(true);
      setChatError(null);
      try {
        const result = await sendCaregiverChat(
          selectedPatientId,
          chatMessage.trim()
        );
        setChatResponse(result);
        setChatMessage("");
      } catch (err) {
        setChatError(
          err instanceof Error ? err.message : "Failed to get AI response"
        );
      } finally {
        setIsChatLoading(false);
      }
    },
    [selectedPatientId, chatMessage, isChatLoading]
  );

  // Fetch status and reset chat when patient changes
  useEffect(() => {
    if (selectedPatientId) {
      fetchStatus();
      setChatMessage("");
      setChatResponse(null);
      setChatError(null);
    }
  }, [selectedPatientId, fetchStatus]);

  // Auto-refresh every 60 seconds
  useEffect(() => {
    if (!selectedPatientId) return;

    const interval = setInterval(() => {
      fetchStatus();
    }, REFRESH_INTERVAL_MS);

    return () => clearInterval(interval);
  }, [selectedPatientId, fetchStatus]);

  if (isLoading) {
    return (
      <main id="main-content" className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Caregiver Dashboard</h1>
          <p className="text-slate-400">Monitor your patient&apos;s glucose</p>
        </div>
        <div
          className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center"
          role="status"
          aria-label="Loading dashboard"
        >
          <Loader2 className="h-8 w-8 text-blue-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">Loading dashboard...</p>
        </div>
      </main>
    );
  }

  if (patients.length === 0) {
    return (
      <main id="main-content" className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">Caregiver Dashboard</h1>
          <p className="text-slate-400">Monitor your patient&apos;s glucose</p>
        </div>
        <div className="bg-slate-900 rounded-xl p-8 border border-slate-800 text-center">
          <Users className="h-10 w-10 text-slate-600 mx-auto mb-3" />
          <p className="text-slate-400 mb-1">No patients linked</p>
          <p className="text-xs text-slate-500">
            Ask your patient to send you an invitation link from the
            GlycemicGPT web app.
          </p>
        </div>
      </main>
    );
  }

  return (
    <main id="main-content" className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Caregiver Dashboard</h1>
          <p className="text-slate-400">
            Monitor your patient&apos;s glucose
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs px-2 py-1 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20">
            <Eye className="h-3 w-3 inline mr-1" />
            Read-only
          </span>
          <button
            type="button"
            onClick={() => fetchStatus(true)}
            disabled={isRefreshing}
            className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors disabled:opacity-50"
            aria-label="Refresh data"
          >
            <RefreshCw
              className={clsx("h-4 w-4", isRefreshing && "animate-spin")}
            />
          </button>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div
          className="bg-red-500/10 rounded-xl p-4 border border-red-500/20"
          role="alert"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-red-400 shrink-0" />
            <p className="text-sm text-red-400">{error}</p>
          </div>
        </div>
      )}

      {/* Patient selector (if multiple patients) */}
      {patients.length > 1 && (
        <div className="bg-slate-900 rounded-xl p-4 border border-slate-800">
          <label
            htmlFor="patient-select"
            className="text-sm font-medium text-slate-300 mb-2 block"
          >
            Select patient
          </label>
          <select
            id="patient-select"
            value={selectedPatientId || ""}
            onChange={(e) => setSelectedPatientId(e.target.value)}
            className="w-full bg-slate-800 text-slate-200 rounded-lg px-3 py-2 text-sm border border-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {patients.map((p) => (
              <option key={p.patient_id} value={p.patient_id}>
                {p.patient_email}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Patient status display */}
      {status && (
        <>
          {/* Patient info bar */}
          <div className="flex items-center gap-2 text-sm text-slate-400">
            <Users className="h-4 w-4" />
            <span>
              Viewing data for{" "}
              <span className="text-blue-400">{status.patient_email}</span>
            </span>
            {lastRefresh && (
              <span className="text-xs text-slate-500 ml-auto">
                Updated{" "}
                {lastRefresh.toLocaleTimeString(undefined, {
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </span>
            )}
          </div>

          {/* Glucose Card */}
          <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-blue-500/10 rounded-lg">
                <Activity className="h-5 w-5 text-blue-400" />
              </div>
              <h2 className="text-lg font-semibold">Current Glucose</h2>
            </div>

            {status.permissions.can_view_glucose ? (
              status.glucose ? (
                <div className="space-y-3">
                  <div className="flex items-baseline gap-3">
                    <span
                      className={clsx(
                        "text-5xl font-bold",
                        getGlucoseColor(status.glucose.value)
                      )}
                    >
                      {status.glucose.value}
                    </span>
                    <span className="text-2xl text-slate-400">mg/dL</span>
                    <span className="text-2xl">
                      {getTrendArrow(status.glucose.trend)}
                    </span>
                  </div>
                  <div className="flex items-center gap-4 text-sm text-slate-500">
                    <span className="flex items-center gap-1">
                      <Clock className="h-3.5 w-3.5" />
                      {status.glucose.minutes_ago < 1
                        ? "Just now"
                        : `${status.glucose.minutes_ago}m ago`}
                    </span>
                    {status.glucose.is_stale && (
                      <span className="text-yellow-400 text-xs">
                        Data may be stale
                      </span>
                    )}
                    {status.glucose.trend_rate !== null && (
                      <span>
                        {status.glucose.trend_rate > 0 ? "+" : ""}
                        {status.glucose.trend_rate.toFixed(1)} mg/dL/min
                      </span>
                    )}
                  </div>
                </div>
              ) : (
                <p className="text-slate-500 text-sm">
                  No glucose data available
                </p>
              )
            ) : (
              <div className="flex items-center gap-2 text-slate-500">
                <Lock className="h-4 w-4" />
                <p className="text-sm">Glucose data not permitted</p>
              </div>
            )}
          </div>

          {/* IoB Card */}
          <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-purple-500/10 rounded-lg">
                <Syringe className="h-5 w-5 text-purple-400" />
              </div>
              <h2 className="text-lg font-semibold">Insulin on Board</h2>
            </div>

            {status.permissions.can_view_iob ? (
              status.iob ? (
                <div className="space-y-2">
                  <p className="text-3xl font-bold text-purple-400">
                    {status.iob.current_iob.toFixed(2)}{" "}
                    <span className="text-base text-slate-400">U</span>
                  </p>
                  {status.iob.is_stale && (
                    <p className="text-yellow-400 text-xs">
                      Data may be stale
                    </p>
                  )}
                </div>
              ) : (
                <p className="text-slate-500 text-sm">
                  No IoB data available
                </p>
              )
            ) : (
              <div className="flex items-center gap-2 text-slate-500">
                <Lock className="h-4 w-4" />
                <p className="text-sm">IoB data not permitted</p>
              </div>
            )}
          </div>

          {/* AI Chat Card - Story 8.4 */}
          <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 bg-cyan-500/10 rounded-lg">
                <MessageSquare className="h-5 w-5 text-cyan-400" />
              </div>
              <h2 className="text-lg font-semibold">
                Ask AI About Your Patient
              </h2>
            </div>

            {status.permissions.can_view_ai_suggestions ? (
              <div className="space-y-4">
                <div aria-live="polite" aria-atomic="true">
                  {chatResponse && (
                    <div className="bg-slate-800/50 rounded-lg p-4 space-y-3">
                      <p className="text-sm text-slate-200 whitespace-pre-wrap">
                        {chatResponse.response}
                      </p>
                      <p className="text-xs text-amber-400/70 italic">
                        {chatResponse.disclaimer}
                      </p>
                    </div>
                  )}
                </div>

                {chatError && (
                  <div className="flex items-center gap-2 text-sm text-red-400">
                    <AlertTriangle className="h-4 w-4 shrink-0" />
                    <p>{chatError}</p>
                  </div>
                )}

                <form onSubmit={handleChatSubmit} className="flex gap-2">
                  <input
                    type="text"
                    value={chatMessage}
                    onChange={(e) => setChatMessage(e.target.value)}
                    placeholder='Ask about your patient, e.g. "How are they doing?"'
                    maxLength={2000}
                    disabled={isChatLoading}
                    className="flex-1 bg-slate-800 text-slate-200 rounded-lg px-3 py-2 text-sm border border-slate-700 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-cyan-500 disabled:opacity-50"
                    aria-label="Ask AI about your patient"
                  />
                  <button
                    type="submit"
                    disabled={isChatLoading || !chatMessage.trim()}
                    className="px-4 py-2 rounded-lg bg-cyan-600 text-white text-sm font-medium hover:bg-cyan-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                    aria-label="Send question"
                  >
                    {isChatLoading ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Send className="h-4 w-4" />
                    )}
                  </button>
                </form>

                <div className="flex flex-wrap gap-2">
                  {[
                    "How is my patient doing?",
                    "Should I be worried?",
                  ].map((q) => (
                    <button
                      key={q}
                      type="button"
                      onClick={() => setChatMessage(q)}
                      disabled={isChatLoading}
                      className="text-xs px-3 py-1 rounded-full bg-slate-800 text-slate-400 hover:bg-slate-700 hover:text-slate-200 transition-colors disabled:opacity-50"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-2 text-slate-500">
                <Lock className="h-4 w-4" />
                <p className="text-sm">AI suggestions not permitted</p>
              </div>
            )}
          </div>

          {/* Permissions summary */}
          <div className="bg-slate-900/50 rounded-xl p-4 border border-slate-800">
            <h3 className="text-sm font-medium text-slate-400 mb-2">
              Your access permissions
            </h3>
            <div className="flex flex-wrap gap-2">
              {[
                {
                  key: "can_view_glucose" as const,
                  label: "Glucose",
                },
                {
                  key: "can_view_history" as const,
                  label: "History",
                },
                { key: "can_view_iob" as const, label: "IoB" },
                {
                  key: "can_view_ai_suggestions" as const,
                  label: "AI Insights",
                },
                {
                  key: "can_receive_alerts" as const,
                  label: "Alerts",
                },
              ].map((perm) => (
                <span
                  key={perm.key}
                  className={clsx(
                    "text-xs px-2 py-1 rounded-full",
                    status.permissions[perm.key]
                      ? "bg-green-500/10 text-green-400"
                      : "bg-slate-800 text-slate-500"
                  )}
                >
                  {status.permissions[perm.key] ? (
                    <Eye className="h-3 w-3 inline mr-1" />
                  ) : (
                    <Lock className="h-3 w-3 inline mr-1" />
                  )}
                  {perm.label}
                </span>
              ))}
            </div>
          </div>
        </>
      )}

      {/* Info card */}
      <div className="bg-slate-900/50 rounded-xl p-4 border border-slate-800">
        <p className="text-xs text-slate-500">
          This is a read-only view. Data access is controlled by the patient.
          Data refreshes automatically every 60 seconds.
        </p>
      </div>
    </main>
  );
}
