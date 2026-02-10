"use client";

/**
 * Story 9.1: Target Glucose Range Configuration
 *
 * Allows users to set their personal low and high target glucose values.
 * Defaults are 70-180 mg/dL (standard diabetes management targets).
 * These values are used by the dashboard display and AI analysis.
 */

import { useState, useEffect, useCallback } from "react";
import {
  Target,
  Loader2,
  AlertTriangle,
  Check,
  ArrowLeft,
  RotateCcw,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";
import {
  getTargetGlucoseRange,
  updateTargetGlucoseRange,
  type TargetGlucoseRangeResponse,
} from "@/lib/api";

const DEFAULTS = { low_target: 70, high_target: 180 };

export default function GlucoseRangePage() {
  const [range, setRange] = useState<TargetGlucoseRangeResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Form state
  const [lowTarget, setLowTarget] = useState<string>("70");
  const [highTarget, setHighTarget] = useState<string>("180");

  const fetchRange = useCallback(async () => {
    try {
      setError(null);
      const data = await getTargetGlucoseRange();
      setRange(data);
      setLowTarget(String(data.low_target));
      setHighTarget(String(data.high_target));
    } catch (err) {
      if (!(err instanceof Error && err.message.includes("401"))) {
        setError(
          err instanceof Error
            ? err.message
            : "Failed to load target glucose range"
        );
      }
      // Use defaults as baseline so the form is still functional
      setRange({
        low_target: DEFAULTS.low_target,
        high_target: DEFAULTS.high_target,
      } as TargetGlucoseRangeResponse);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRange();
  }, [fetchRange]);

  // Auto-clear success message after 5 seconds
  useEffect(() => {
    if (!success) return;
    const timer = setTimeout(() => setSuccess(null), 5000);
    return () => clearTimeout(timer);
  }, [success]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setError(null);
    setSuccess(null);

    const low = parseFloat(lowTarget);
    const high = parseFloat(highTarget);

    if (isNaN(low) || isNaN(high)) {
      setError("Please enter valid numbers");
      setIsSaving(false);
      return;
    }

    if (low >= high) {
      setError("Low target must be less than high target");
      setIsSaving(false);
      return;
    }

    try {
      const updated = await updateTargetGlucoseRange({
        low_target: low,
        high_target: high,
      });
      setRange(updated);
      setSuccess("Target glucose range updated successfully");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to update target range"
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleReset = async () => {
    setIsSaving(true);
    setError(null);
    setSuccess(null);

    try {
      const updated = await updateTargetGlucoseRange({
        low_target: DEFAULTS.low_target,
        high_target: DEFAULTS.high_target,
      });
      setRange(updated);
      setLowTarget(String(DEFAULTS.low_target));
      setHighTarget(String(DEFAULTS.high_target));
      setSuccess("Target glucose range reset to defaults");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to reset target range"
      );
    } finally {
      setIsSaving(false);
    }
  };

  const lowNum = parseFloat(lowTarget);
  const highNum = parseFloat(highTarget);
  const hasChanges =
    range &&
    (parseFloat(lowTarget) !== range.low_target ||
      parseFloat(highTarget) !== range.high_target);
  const isValid =
    !isNaN(lowNum) &&
    !isNaN(highNum) &&
    lowNum >= 40 &&
    lowNum <= 200 &&
    highNum >= 80 &&
    highNum <= 400 &&
    lowNum < highNum;

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <Link
          href="/dashboard/settings"
          className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-300 mb-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Settings
        </Link>
        <h1 className="text-2xl font-bold">Target Glucose Range</h1>
        <p className="text-slate-400">
          Set your personal target range for dashboard display and AI analysis
        </p>
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

      {/* Success state */}
      {success && (
        <div
          className="bg-green-500/10 rounded-xl p-4 border border-green-500/20"
          role="status"
        >
          <div className="flex items-center gap-2">
            <Check className="h-4 w-4 text-green-400 shrink-0" />
            <p className="text-sm text-green-400">{success}</p>
          </div>
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div
          className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center"
          role="status"
          aria-label="Loading target glucose range"
        >
          <Loader2 className="h-8 w-8 text-blue-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">Loading target range...</p>
        </div>
      )}

      {/* Configuration form */}
      {!isLoading && (
        <div className="bg-slate-900 rounded-xl border border-slate-800 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-green-500/10 rounded-lg">
              <Target className="h-5 w-5 text-green-400" />
            </div>
            <div>
              <h2 className="text-lg font-semibold">Range Settings</h2>
              <p className="text-xs text-slate-500">
                Values used by Time in Range calculations and AI suggestions
              </p>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
              {/* Low target */}
              <div>
                <label
                  htmlFor="low-target"
                  className="block text-sm font-medium text-slate-300 mb-1"
                >
                  Low Target (mg/dL)
                </label>
                <input
                  id="low-target"
                  type="number"
                  min={40}
                  max={200}
                  step={1}
                  value={lowTarget}
                  onChange={(e) => setLowTarget(e.target.value)}
                  disabled={isSaving}
                  className={clsx(
                    "w-full rounded-lg border px-3 py-2 text-sm",
                    "bg-slate-800 border-slate-700 text-slate-200",
                    "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                    "placeholder:text-slate-500",
                    "disabled:opacity-50 disabled:cursor-not-allowed"
                  )}
                  aria-describedby="low-target-hint"
                />
                <p id="low-target-hint" className="text-xs text-slate-500 mt-1">
                  Range: 40-200 mg/dL. Default: 70 mg/dL
                </p>
              </div>

              {/* High target */}
              <div>
                <label
                  htmlFor="high-target"
                  className="block text-sm font-medium text-slate-300 mb-1"
                >
                  High Target (mg/dL)
                </label>
                <input
                  id="high-target"
                  type="number"
                  min={80}
                  max={400}
                  step={1}
                  value={highTarget}
                  onChange={(e) => setHighTarget(e.target.value)}
                  disabled={isSaving}
                  className={clsx(
                    "w-full rounded-lg border px-3 py-2 text-sm",
                    "bg-slate-800 border-slate-700 text-slate-200",
                    "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                    "placeholder:text-slate-500",
                    "disabled:opacity-50 disabled:cursor-not-allowed"
                  )}
                  aria-describedby="high-target-hint"
                />
                <p
                  id="high-target-hint"
                  className="text-xs text-slate-500 mt-1"
                >
                  Range: 80-400 mg/dL. Default: 180 mg/dL
                </p>
              </div>
            </div>

            {/* Visual preview */}
            {isValid && (
              <div className="bg-slate-800/50 rounded-lg p-4 border border-slate-700/50">
                <p className="text-xs text-slate-500 mb-2">Preview</p>
                <p className="text-lg font-semibold text-green-400">
                  Target: {lowNum}-{highNum} mg/dL
                </p>
              </div>
            )}

            {/* Actions */}
            <div className="flex items-center gap-3 pt-2">
              <button
                type="submit"
                disabled={isSaving || !hasChanges || !isValid}
                className={clsx(
                  "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
                  "bg-blue-600 text-white hover:bg-blue-500",
                  "transition-colors",
                  "focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500",
                  "disabled:opacity-50 disabled:cursor-not-allowed"
                )}
              >
                {isSaving ? (
                  <Loader2
                    className="h-4 w-4 animate-spin"
                    aria-hidden="true"
                  />
                ) : (
                  <Check className="h-4 w-4" aria-hidden="true" />
                )}
                {isSaving ? "Saving..." : "Save Changes"}
              </button>

              <button
                type="button"
                onClick={handleReset}
                disabled={
                  isSaving ||
                  (range?.low_target === DEFAULTS.low_target &&
                    range?.high_target === DEFAULTS.high_target)
                }
                className={clsx(
                  "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
                  "bg-slate-800 text-slate-300 hover:bg-slate-700",
                  "transition-colors",
                  "focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-500",
                  "disabled:opacity-50 disabled:cursor-not-allowed"
                )}
              >
                <RotateCcw className="h-4 w-4" aria-hidden="true" />
                Reset to Defaults
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Info card */}
      <div className="bg-slate-900/50 rounded-xl p-4 border border-slate-800">
        <p className="text-xs text-slate-500">
          Your target glucose range determines what counts as &quot;in range&quot; on the
          dashboard Time in Range bar and influences AI-generated suggestions.
          The standard range for most people with diabetes is 70-180 mg/dL.
          Consult your healthcare provider before changing these values.
        </p>
      </div>
    </div>
  );
}
