"use client";

/**
 * Alerts & Thresholds Page
 *
 * Story 6.1: Alert Threshold Configuration
 * Allows users to configure glucose and IoB alert thresholds.
 * Settings take effect immediately upon save.
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
} from "lucide-react";
import clsx from "clsx";
import {
  getAlertThresholds,
  updateAlertThresholds,
  type AlertThresholdUpdate,
} from "@/lib/api";

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

export default function AlertsPage() {
  const [formValues, setFormValues] = useState<AlertThresholdUpdate>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);

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
  }, [fetchThresholds]);

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

      {/* Active alerts placeholder */}
      <div className="bg-slate-900/50 rounded-xl p-6 border border-slate-800">
        <div className="flex items-center gap-3">
          <div className="h-3 w-3 rounded-full bg-green-500" />
          <span className="text-slate-300">No active alerts</span>
        </div>
      </div>
    </div>
  );
}
