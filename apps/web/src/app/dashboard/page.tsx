"use client";

/**
 * Dashboard Page
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Story 4.2: GlucoseHero Component
 * Story 4.4: Time in Range Bar Component
 * Story 4.5: Real-Time Updates via SSE
 * Main dashboard view showing glucose data and metrics.
 */

import { Activity, Clock } from "lucide-react";

import {
  GlucoseHero,
  TimeInRangeBar,
  ConnectionStatusBanner,
} from "@/components/dashboard";
import { useGlucoseStreamContext } from "@/providers";

export default function DashboardPage() {
  // Real-time glucose data from SSE stream (Story 4.5)
  const {
    glucose,
    isLive,
    isReconnecting,
    error,
    reconnect,
  } = useGlucoseStreamContext();

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
    <div className="space-y-6">
      {/* Connection status banner - Story 4.5 */}
      <ConnectionStatusBanner
        isReconnecting={isReconnecting}
        hasError={!!error}
        errorMessage={error?.message}
        onReconnect={reconnect}
      />

      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-slate-400">Your glucose overview at a glance</p>
      </div>

      {/* Glucose hero - Story 4.2 */}
      {/* Issue 3 fix: trend is now pre-mapped by the hook, no mapTrend needed */}
      {/* Issue 6 fix: Pass cob prop */}
      <GlucoseHero
        value={glucoseValue}
        trend={glucoseTrend}
        iob={iob}
        cob={cob}
        isLoading={!isLive && !glucose}
      />

      {/* Metrics grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Time in Range Card */}
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-green-500/10 rounded-lg">
              <Activity className="h-5 w-5 text-green-400" />
            </div>
            <span className="text-slate-400 text-sm">Time in Range (24h)</span>
          </div>
          <p className="text-3xl font-bold text-green-400">78%</p>
          <p className="text-slate-500 text-xs mt-1">Target: 70-180 mg/dL</p>
        </div>

        {/* Last Updated Card */}
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-emerald-500/10 rounded-lg">
              <Clock className="h-5 w-5 text-emerald-400" />
            </div>
            <span className="text-slate-400 text-sm">Last Updated</span>
          </div>
          <p className="text-3xl font-bold text-emerald-400">
            {getLastUpdatedText()}
          </p>
          <p className="text-slate-500 text-xs mt-1">{getFreshnessText()}</p>
        </div>
      </div>

      {/* Time in Range bar - Story 4.4 */}
      <TimeInRangeBar data={mockTimeInRangeData} period="24h" />
    </div>
  );
}
