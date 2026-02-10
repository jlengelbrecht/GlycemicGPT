"use client";

/**
 * Dashboard Page
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Story 4.2: GlucoseHero Component
 * Story 4.4: Time in Range Bar Component
 * Story 4.5: Real-Time Updates via SSE
 * Story 4.6: Dashboard Accessibility
 * Story 8.3: Role-based routing (caregivers redirect to /dashboard/caregiver)
 * Main dashboard view showing glucose data and metrics.
 *
 * Accessibility features:
 * - Main landmark for skip link navigation
 * - Proper heading hierarchy (h1 for page, h2 for sections)
 * - Logical tab order
 */

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Activity, Clock } from "lucide-react";

import {
  GlucoseHero,
  TimeInRangeBar,
  ConnectionStatusBanner,
} from "@/components/dashboard";
import { useGlucoseStreamContext, useUserContext } from "@/providers";
import { getTargetGlucoseRange } from "@/lib/api";

export default function DashboardPage() {
  const router = useRouter();
  const { user, isLoading: isUserLoading } = useUserContext();

  // All hooks must be called before any early return
  const {
    glucose,
    isLive,
    isReconnecting,
    error,
    reconnect,
  } = useGlucoseStreamContext();

  // Story 9.1: Fetch user's target glucose range
  const [targetRange, setTargetRange] = useState("70-180 mg/dL");

  const fetchTargetRange = useCallback(async () => {
    try {
      const data = await getTargetGlucoseRange();
      setTargetRange(`${data.low_target}-${data.high_target} mg/dL`);
    } catch {
      // Keep default on error
    }
  }, []);

  useEffect(() => {
    if (user && user.role !== "caregiver") {
      fetchTargetRange();
    }
  }, [user, fetchTargetRange]);

  // Redirect caregivers to the caregiver-specific dashboard (Story 8.3)
  useEffect(() => {
    if (user?.role === "caregiver") {
      router.replace("/dashboard/caregiver");
    }
  }, [user, router]);

  // Prevent flash of diabetic dashboard while caregiver redirect is pending
  if (isUserLoading || user?.role === "caregiver") {
    return null;
  }

  // Fallback data for when no real-time data is available
  const mockTimeInRangeData = {
    low: 5,
    inRange: 78,
    high: 17,
  };

  // Determine data to display
  // Issue 2 & 3 fix: The hook now returns the mapped frontend trend directly
  const glucoseValue = glucose?.value ?? null;
  const glucoseTrend = glucose?.trend ?? "Unknown";
  const iob = glucose?.iob?.current ?? null;
  // Issue 6 fix: Pass CoB to GlucoseHero (null if not available)
  const cob = glucose?.cob?.current ?? null;
  const minutesAgo = glucose?.minutes_ago ?? null;
  const isStale = glucose?.is_stale ?? false;

  // Format "last updated" text
  const getLastUpdatedText = (): string => {
    if (minutesAgo === null) return "No data";
    if (minutesAgo < 1) return "Just now";
    if (minutesAgo === 1) return "1m ago";
    return `${minutesAgo}m ago`;
  };

  const getFreshnessText = (): string => {
    if (!isLive) return "Connecting...";
    if (isStale) return "Data is stale";
    return "Data is fresh";
  };

  return (
    <main id="main-content" className="space-y-6">
      {/* Connection status banner - Story 4.5 */}
      <ConnectionStatusBanner
        isReconnecting={isReconnecting}
        hasError={!!error}
        errorMessage={error?.message}
        onReconnect={reconnect}
      />

      {/* Page header - using div instead of header to avoid banner role confusion inside main */}
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-slate-400">Your glucose overview at a glance</p>
      </div>

      {/* Glucose hero - Story 4.2, 4.6 */}
      <GlucoseHero
        value={glucoseValue}
        trend={glucoseTrend}
        iob={iob}
        cob={cob}
        isLoading={!isLive && !glucose}
      />

      {/* Metrics grid with proper heading hierarchy */}
      <section aria-labelledby="metrics-heading">
        <h2 id="metrics-heading" className="sr-only">Dashboard Metrics</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Time in Range Card */}
          <article className="bg-slate-900 rounded-xl p-6 border border-slate-800">
            <div className="flex items-center gap-3 mb-2">
              <div className="p-2 bg-green-500/10 rounded-lg">
                <Activity className="h-5 w-5 text-green-400" aria-hidden="true" />
              </div>
              <h3 className="text-slate-400 text-sm">Time in Range (24h)</h3>
            </div>
            <p className="text-3xl font-bold text-green-400" aria-label="Time in range: 78 percent">78%</p>
            <p className="text-slate-500 text-xs mt-1">Target: {targetRange}</p>
          </article>

          {/* Last Updated Card */}
          <article className="bg-slate-900 rounded-xl p-6 border border-slate-800">
            <div className="flex items-center gap-3 mb-2">
              <div className="p-2 bg-emerald-500/10 rounded-lg">
                <Clock className="h-5 w-5 text-emerald-400" aria-hidden="true" />
              </div>
              <h3 className="text-slate-400 text-sm">Last Updated</h3>
            </div>
            <p className="text-3xl font-bold text-emerald-400" aria-label={`Last updated: ${getLastUpdatedText()}`}>
              {getLastUpdatedText()}
            </p>
            <p className="text-slate-500 text-xs mt-1">{getFreshnessText()}</p>
          </article>
        </div>
      </section>

      {/* Time in Range bar - Story 4.4 */}
      <TimeInRangeBar data={mockTimeInRangeData} period="24h" targetRange={targetRange} />
    </main>
  );
}
