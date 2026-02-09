"use client";

/**
 * Alerts & Thresholds Page
 *
 * Story 6.1: Alert Threshold Configuration
 * Story 6.2: Predictive Alert Engine - Active alerts display
 * Story 6.3: Notification Preferences
 *
 * Allows users to configure glucose and IoB alert thresholds,
 * view active predictive alerts with acknowledgment, and
 * manage notification preferences (sound, browser notifications).
 *
 * Accessibility: labeled inputs, error alerts, keyboard navigation.
 */

import { useState, useEffect, useCallback } from "react";
import {
  Bell,
  Save,
  RotateCcw,
  Loader2,
  AlertTriangle,
  Check,
  CheckCircle,
  Clock,
  TrendingDown,
  TrendingUp,
  Syringe,
  Volume2,
  BellRing,
} from "lucide-react";
import clsx from "clsx";
import {
  getAlertThresholds,
  updateAlertThresholds,
  getActiveAlerts,
  acknowledgeAlert,
  type AlertThresholdUpdate,
  type PredictiveAlert,
} from "@/lib/api";
import { useAlertNotifications } from "@/providers";
import { requestNotificationPermission } from "@/lib/browser-notifications";

const DEFAULTS = {
  low_warning: 70,
  urgent_low: 55,
  high_warning: 180,
  urgent_high: 250,
  iob_warning: 3.0,
};

interface ThresholdFieldConfig {
  key: keyof AlertThresholdUpdate;
  label: string;
  unit: string;
  min: number;
  max: number;
  step: number;
  description: string;
  color: string;
}

const THRESHOLD_FIELDS: ThresholdFieldConfig[] = [
  {
    key: "urgent_low",
    label: "Urgent Low",
    unit: "mg/dL",
    min: 30,
    max: 80,
    step: 1,
    description: "Critical low glucose alert",
    color: "text-red-400",
  },
  {
    key: "low_warning",
    label: "Low Warning",
    unit: "mg/dL",
    min: 40,
    max: 100,
    step: 1,
    description: "Low glucose warning",
    color: "text-amber-400",
  },
  {
    key: "high_warning",
    label: "High Warning",
    unit: "mg/dL",
    min: 120,
    max: 300,
    step: 1,
    description: "High glucose warning",
    color: "text-amber-400",
  },
  {
    key: "urgent_high",
    label: "Urgent High",
    unit: "mg/dL",
    min: 150,
    max: 400,
    step: 1,
    description: "Critical high glucose alert",
    color: "text-red-400",
  },
  {
    key: "iob_warning",
    label: "IoB Warning",
    unit: "units",
    min: 0.5,
    max: 20,
    step: 0.1,
    description: "Insulin on Board warning threshold",
    color: "text-purple-400",
  },
];

const SEVERITY_CONFIG: Record<
  string,
  { bg: string; border: string; text: string; icon: string }
> = {
  emergency: {
    bg: "bg-red-500/15",
    border: "border-red-500/30",
    text: "text-red-400",
    icon: "text-red-400",
  },
  urgent: {
    bg: "bg-red-500/10",
    border: "border-red-500/20",
    text: "text-red-400",
    icon: "text-red-400",
  },
  warning: {
    bg: "bg-amber-500/10",
    border: "border-amber-500/20",
    text: "text-amber-400",
    icon: "text-amber-400",
  },
  info: {
    bg: "bg-blue-500/10",
    border: "border-blue-500/20",
    text: "text-blue-400",
    icon: "text-blue-400",
  },
};

function getAlertIcon(alertType: string) {
  if (alertType.includes("low")) return TrendingDown;
  if (alertType.includes("high")) return TrendingUp;
  if (alertType === "iob_warning") return Syringe;
  return AlertTriangle;
}

function formatTimeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ago`;
}

export default function AlertsPage() {
  const [formValues, setFormValues] = useState<AlertThresholdUpdate>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);
  const [activeAlerts, setActiveAlerts] = useState<PredictiveAlert[]>([]);
  const [alertsLoading, setAlertsLoading] = useState(true);
  const [acknowledgingId, setAcknowledgingId] = useState<string | null>(null);

  // Story 6.3: Notification preferences
  const { preferences, setPreferences } = useAlertNotifications();

  const fetchAlerts = useCallback(async () => {
    try {
      const data = await getActiveAlerts();
      setActiveAlerts(data.alerts);
    } catch {
      // Silently fail for alerts - user may not be authenticated yet
      setActiveAlerts([]);
    } finally {
      setAlertsLoading(false);
    }
  }, []);

  const handleAcknowledge = async (alertId: string) => {
    setAcknowledgingId(alertId);
    try {
      await acknowledgeAlert(alertId);
      setActiveAlerts((prev) => prev.filter((a) => a.id !== alertId));
    } catch {
      // Refresh alerts from server to get current state
      await fetchAlerts();
    } finally {
      setAcknowledgingId(null);
    }
  };

  const fetchThresholds = useCallback(async () => {
    try {
      setError(null);
      const data = await getAlertThresholds();
      setFormValues({
        low_warning: data.low_warning,
        urgent_low: data.urgent_low,
        high_warning: data.high_warning,
        urgent_high: data.urgent_high,
        iob_warning: data.iob_warning,
      });
      setHasChanges(false);
    } catch (err) {
      if (!(err instanceof Error && err.message.includes("401"))) {
        setError(
          err instanceof Error ? err.message : "Failed to load thresholds"
        );
      }
      // Show defaults on any error so inputs aren't empty
      setFormValues({ ...DEFAULTS });
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchThresholds();
    fetchAlerts();

    // Poll for new alerts every 60 seconds
    const interval = setInterval(fetchAlerts, 60000);
    return () => clearInterval(interval);
  }, [fetchThresholds, fetchAlerts]);

  const handleChange = (key: keyof AlertThresholdUpdate, value: string) => {
    const numValue = parseFloat(value);
    if (isNaN(numValue)) return;

    setFormValues((prev) => ({ ...prev, [key]: numValue }));
    setHasChanges(true);
    setSaveSuccess(false);
  };

  const handleSave = async () => {
    // Client-side ordering validation
    const ul = formValues.urgent_low ?? DEFAULTS.urgent_low;
    const lw = formValues.low_warning ?? DEFAULTS.low_warning;
    const hw = formValues.high_warning ?? DEFAULTS.high_warning;
    const uh = formValues.urgent_high ?? DEFAULTS.urgent_high;

    if (ul >= lw) {
      setError(`Urgent Low (${ul}) must be less than Low Warning (${lw})`);
      return;
    }
    if (lw >= hw) {
      setError(`Low Warning (${lw}) must be less than High Warning (${hw})`);
      return;
    }
    if (hw >= uh) {
      setError(`High Warning (${hw}) must be less than Urgent High (${uh})`);
      return;
    }

    setIsSaving(true);
    setError(null);
    setSaveSuccess(false);

    try {
      const data = await updateAlertThresholds(formValues);
      setFormValues({
        low_warning: data.low_warning,
        urgent_low: data.urgent_low,
        high_warning: data.high_warning,
        urgent_high: data.urgent_high,
        iob_warning: data.iob_warning,
      });
      setHasChanges(false);
      setSaveSuccess(true);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to save thresholds"
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = () => {
    setFormValues({ ...DEFAULTS });
    setHasChanges(true);
    setSaveSuccess(false);
  };

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold">Alerts & Thresholds</h1>
        <p className="text-slate-400">
          Configure your glucose and insulin alert thresholds
        </p>
      </div>

      {/* Loading state */}
      {isLoading && (
        <div
          className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center"
          role="status"
          aria-label="Loading thresholds"
        >
          <Loader2 className="h-8 w-8 text-blue-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">Loading thresholds...</p>
        </div>
      )}

      {/* Error state */}
      {error && !isLoading && (
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

      {/* Success state */}
      {saveSuccess && (
        <div
          className="bg-green-500/10 rounded-xl p-4 border border-green-500/20"
          role="status"
        >
          <div className="flex items-center gap-2">
            <Check className="h-4 w-4 text-green-400 shrink-0" />
            <p className="text-sm text-green-400">
              Thresholds saved successfully. Changes take effect immediately.
            </p>
          </div>
        </div>
      )}

      {/* Threshold configuration form */}
      {!isLoading && (
        <div className="bg-slate-900 rounded-xl border border-slate-800 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-amber-500/10 rounded-lg">
              <Bell className="h-5 w-5 text-amber-400" />
            </div>
            <div>
              <h2 className="text-lg font-semibold">Alert Thresholds</h2>
              <p className="text-xs text-slate-500">
                Alerts trigger when glucose or IoB crosses these thresholds
              </p>
            </div>
          </div>

          <div className="space-y-5">
            {/* Glucose thresholds section */}
            <div>
              <h3 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-3">
                Glucose Thresholds
              </h3>
              <div className="grid gap-4 sm:grid-cols-2">
                {THRESHOLD_FIELDS.filter((f) => f.unit === "mg/dL").map(
                  (field) => (
                    <div key={field.key}>
                      <label
                        htmlFor={field.key}
                        className="block text-sm font-medium text-slate-300 mb-1"
                      >
                        <span className={field.color}>{field.label}</span>
                      </label>
                      <p className="text-xs text-slate-500 mb-2">
                        {field.description}
                      </p>
                      <div className="flex items-center gap-2">
                        <input
                          id={field.key}
                          type="number"
                          min={field.min}
                          max={field.max}
                          step={field.step}
                          value={formValues[field.key] ?? ""}
                          onChange={(e) =>
                            handleChange(field.key, e.target.value)
                          }
                          className={clsx(
                            "w-full rounded-lg border px-3 py-2 text-sm",
                            "bg-slate-800 border-slate-700 text-slate-200",
                            "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                            "placeholder:text-slate-500"
                          )}
                          aria-describedby={`${field.key}-range`}
                        />
                        <span className="text-xs text-slate-500 shrink-0">
                          {field.unit}
                        </span>
                      </div>
                      <p
                        id={`${field.key}-range`}
                        className="text-xs text-slate-600 mt-1"
                      >
                        Range: {field.min}–{field.max} {field.unit}
                      </p>
                    </div>
                  )
                )}
              </div>
            </div>

            {/* IoB threshold section */}
            <div>
              <h3 className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-3">
                Insulin on Board
              </h3>
              {THRESHOLD_FIELDS.filter((f) => f.unit === "units").map(
                (field) => (
                  <div key={field.key} className="max-w-xs">
                    <label
                      htmlFor={field.key}
                      className="block text-sm font-medium text-slate-300 mb-1"
                    >
                      <span className={field.color}>{field.label}</span>
                    </label>
                    <p className="text-xs text-slate-500 mb-2">
                      {field.description}
                    </p>
                    <div className="flex items-center gap-2">
                      <input
                        id={field.key}
                        type="number"
                        min={field.min}
                        max={field.max}
                        step={field.step}
                        value={formValues[field.key] ?? ""}
                        onChange={(e) =>
                          handleChange(field.key, e.target.value)
                        }
                        className={clsx(
                          "w-full rounded-lg border px-3 py-2 text-sm",
                          "bg-slate-800 border-slate-700 text-slate-200",
                          "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                          "placeholder:text-slate-500"
                        )}
                        aria-describedby={`${field.key}-range`}
                      />
                      <span className="text-xs text-slate-500 shrink-0">
                        {field.unit}
                      </span>
                    </div>
                    <p
                      id={`${field.key}-range`}
                      className="text-xs text-slate-600 mt-1"
                    >
                      Range: {field.min}–{field.max} {field.unit}
                    </p>
                  </div>
                )
              )}
            </div>
          </div>

          {/* Action buttons */}
          <div
            className="flex items-center gap-3 mt-6 pt-4 border-t border-slate-800"
            role="group"
            aria-label="Threshold actions"
          >
            <button
              onClick={handleSave}
              disabled={isSaving || !hasChanges}
              className={clsx(
                "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
                "bg-blue-600 text-white hover:bg-blue-500",
                "transition-colors",
                "focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500",
                "disabled:opacity-50 disabled:cursor-not-allowed"
              )}
              aria-label="Save threshold changes"
            >
              {isSaving ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              ) : (
                <Save className="h-4 w-4" aria-hidden="true" />
              )}
              {isSaving ? "Saving..." : "Save Changes"}
            </button>
            <button
              onClick={handleReset}
              disabled={isSaving}
              className={clsx(
                "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
                "bg-slate-800 text-slate-300 hover:bg-slate-700",
                "transition-colors",
                "focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-500",
                "disabled:opacity-50 disabled:cursor-not-allowed"
              )}
              aria-label="Reset thresholds to defaults"
            >
              <RotateCcw className="h-4 w-4" aria-hidden="true" />
              Reset to Defaults
            </button>
          </div>
        </div>
      )}

      {/* Active alerts section */}
      <div className="bg-slate-900 rounded-xl border border-slate-800 p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-2 bg-red-500/10 rounded-lg">
            <AlertTriangle className="h-5 w-5 text-red-400" />
          </div>
          <div>
            <h2 className="text-lg font-semibold">Active Alerts</h2>
            <p className="text-xs text-slate-500">
              Predictive and threshold-based alerts
            </p>
          </div>
          {activeAlerts.length > 0 && (
            <span className="ml-auto text-xs bg-red-500/20 text-red-400 px-2 py-1 rounded-full">
              {activeAlerts.length} active
            </span>
          )}
        </div>

        {alertsLoading && (
          <div
            className="text-center py-4"
            role="status"
            aria-label="Loading alerts"
          >
            <Loader2 className="h-5 w-5 text-slate-400 animate-spin mx-auto" />
          </div>
        )}

        {!alertsLoading && activeAlerts.length === 0 && (
          <div className="flex items-center gap-3 py-4">
            <div className="h-3 w-3 rounded-full bg-green-500" />
            <span className="text-slate-300">No active alerts</span>
          </div>
        )}

        {!alertsLoading && activeAlerts.length > 0 && (
          <div className="space-y-3" role="list" aria-label="Active alerts">
            {activeAlerts.map((alert) => {
              const config = SEVERITY_CONFIG[alert.severity] ?? SEVERITY_CONFIG.info;
              const Icon = getAlertIcon(alert.alert_type);
              return (
                <div
                  key={alert.id}
                  className={clsx(
                    "rounded-lg p-4 border",
                    config.bg,
                    config.border
                  )}
                  role="listitem"
                >
                  <div className="flex items-start gap-3">
                    <Icon
                      className={clsx("h-5 w-5 mt-0.5 shrink-0", config.icon)}
                      aria-hidden="true"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span
                          className={clsx(
                            "text-xs font-medium uppercase tracking-wider",
                            config.text
                          )}
                        >
                          {alert.severity}
                        </span>
                        {alert.source === "predictive" && (
                          <span className="text-xs text-slate-500">
                            Predicted
                          </span>
                        )}
                      </div>
                      <p className={clsx("text-sm", config.text)}>
                        {alert.message}
                      </p>
                      <div className="flex items-center gap-3 mt-2 text-xs text-slate-500">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" aria-hidden="true" />
                          {formatTimeAgo(alert.created_at)}
                        </span>
                        {alert.iob_value != null && (
                          <span>IoB: {alert.iob_value.toFixed(1)}u</span>
                        )}
                        {alert.prediction_minutes != null && (
                          <span>
                            {alert.prediction_minutes}min prediction
                          </span>
                        )}
                      </div>
                    </div>
                    <button
                      onClick={() => handleAcknowledge(alert.id)}
                      disabled={acknowledgingId === alert.id}
                      className={clsx(
                        "flex items-center gap-1 px-3 py-1.5 rounded-md text-xs font-medium",
                        "bg-slate-800/50 text-slate-300 hover:bg-slate-700",
                        "transition-colors shrink-0",
                        "focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-500",
                        "disabled:opacity-50 disabled:cursor-not-allowed"
                      )}
                      aria-label={`Acknowledge ${alert.alert_type.replace("_", " ")} alert`}
                    >
                      {acknowledgingId === alert.id ? (
                        <Loader2
                          className="h-3 w-3 animate-spin"
                          aria-hidden="true"
                        />
                      ) : (
                        <CheckCircle className="h-3 w-3" aria-hidden="true" />
                      )}
                      Acknowledge
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Story 6.3: Notification Preferences */}
      <div className="bg-slate-900 rounded-xl border border-slate-800 p-6">
        <div className="flex items-center gap-3 mb-4">
          <div className="p-2 bg-blue-500/10 rounded-lg">
            <BellRing className="h-5 w-5 text-blue-400" />
          </div>
          <div>
            <h2 className="text-lg font-semibold">Notification Preferences</h2>
            <p className="text-xs text-slate-500">
              Control how you receive alert notifications
            </p>
          </div>
        </div>

        <div className="space-y-4">
          {/* Sound toggle */}
          <label className="flex items-center gap-3 cursor-pointer group">
            <input
              id="pref-sound"
              type="checkbox"
              checked={preferences.soundEnabled}
              onChange={(e) =>
                setPreferences({
                  ...preferences,
                  soundEnabled: e.target.checked,
                })
              }
              className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-blue-500 focus:ring-blue-500 focus:ring-offset-0"
              aria-describedby="pref-sound-desc"
            />
            <Volume2
              className="h-4 w-4 text-slate-400 group-hover:text-slate-300"
              aria-hidden="true"
            />
            <div>
              <span className="text-sm text-slate-300 group-hover:text-slate-200">
                Alert sounds
              </span>
              <p id="pref-sound-desc" className="text-xs text-slate-500">
                Play audio tones when alerts are received
              </p>
            </div>
          </label>

          {/* Browser notifications toggle */}
          <label className="flex items-center gap-3 cursor-pointer group">
            <input
              id="pref-browser-notif"
              type="checkbox"
              checked={preferences.browserNotificationsEnabled}
              onChange={async (e) => {
                if (e.target.checked) {
                  const permission = await requestNotificationPermission();
                  setPreferences({
                    ...preferences,
                    browserNotificationsEnabled: permission === "granted",
                  });
                } else {
                  setPreferences({
                    ...preferences,
                    browserNotificationsEnabled: false,
                  });
                }
              }}
              className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-blue-500 focus:ring-blue-500 focus:ring-offset-0"
              aria-describedby="pref-browser-notif-desc"
            />
            <Bell
              className="h-4 w-4 text-slate-400 group-hover:text-slate-300"
              aria-hidden="true"
            />
            <div>
              <span className="text-sm text-slate-300 group-hover:text-slate-200">
                Browser notifications
              </span>
              <p id="pref-browser-notif-desc" className="text-xs text-slate-500">
                Show OS-level notifications for urgent and emergency alerts
              </p>
            </div>
          </label>
        </div>
      </div>
    </div>
  );
}
