"use client";

/**
 * GlucoseTrendChart Component
 *
 * Dexcom-style glucose trend chart with colored dots, target range band,
 * bolus delivery markers, basal rate area, and time period selector.
 */

import { useMemo, useEffect, useRef } from "react";
import {
  ResponsiveContainer,
  ComposedChart,
  Scatter,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
  ReferenceLine,
  Cell,
} from "recharts";
import clsx from "clsx";
import { type GlucoseHistoryReading, type PumpEventReading } from "@/lib/api";
import { GLUCOSE_THRESHOLDS } from "./glucose-hero";
import {
  type ChartTimePeriod,
  useGlucoseHistory,
} from "@/hooks/use-glucose-history";
import { usePumpEvents } from "@/hooks/use-pump-events";

// --- Color mapping by glucose classification ---

export function getPointColor(
  value: number,
  thresholds?: { urgentLow: number; low: number; high: number; urgentHigh: number }
): string {
  const t = thresholds ?? {
    urgentLow: GLUCOSE_THRESHOLDS.URGENT_LOW,
    low: GLUCOSE_THRESHOLDS.LOW,
    high: GLUCOSE_THRESHOLDS.HIGH,
    urgentHigh: GLUCOSE_THRESHOLDS.URGENT_HIGH,
  };
  if (value < t.urgentLow) return "#dc2626"; // red-600
  if (value < t.low) return "#f59e0b"; // amber-500
  if (value <= t.high) return "#22c55e"; // green-500
  if (value <= t.urgentHigh) return "#f59e0b"; // amber-500
  return "#dc2626"; // red-600
}

// --- Time period buttons ---

const PERIODS: { value: ChartTimePeriod; label: string }[] = [
  { value: "3h", label: "3H" },
  { value: "6h", label: "6H" },
  { value: "12h", label: "12H" },
  { value: "24h", label: "24H" },
];

export const PERIOD_TO_MS: Record<ChartTimePeriod, number> = {
  "3h": 3 * 60 * 60 * 1000,
  "6h": 6 * 60 * 60 * 1000,
  "12h": 12 * 60 * 60 * 1000,
  "24h": 24 * 60 * 60 * 1000,
};

// --- Chart data point ---

interface ChartPoint {
  timestamp: number;
  value: number;
  color: string;
  iso: string;
}

function transformReadings(
  readings: GlucoseHistoryReading[],
  thresholds?: { urgentLow: number; low: number; high: number; urgentHigh: number }
): ChartPoint[] {
  return readings
    .map((r) => ({
      timestamp: new Date(r.reading_timestamp).getTime(),
      value: r.value,
      color: getPointColor(r.value, thresholds),
      iso: r.reading_timestamp,
    }))
    .sort((a, b) => a.timestamp - b.timestamp);
}

// --- Pump event data transformations ---

interface BolusPoint {
  timestamp: number;
  units: number;
  isAutomated: boolean;
  isCorrection: boolean;
  label: string;
}

interface BasalPoint {
  timestamp: number;
  rate: number;
}

function transformBolusEvents(events: PumpEventReading[]): BolusPoint[] {
  return events
    .filter((e) => (e.event_type === "bolus" || e.event_type === "correction") && e.units != null && e.units > 0)
    .map((e) => ({
      timestamp: new Date(e.event_timestamp).getTime(),
      units: e.units!,
      isAutomated: e.is_automated,
      isCorrection: e.event_type === "correction",
      label: `${e.units!.toFixed(1)}u`,
    }))
    .sort((a, b) => a.timestamp - b.timestamp);
}

function transformBasalEvents(events: PumpEventReading[]): BasalPoint[] {
  return events
    .filter((e) => e.event_type === "basal" && e.units != null)
    .map((e) => ({
      timestamp: new Date(e.event_timestamp).getTime(),
      rate: e.units!,
    }))
    .sort((a, b) => a.timestamp - b.timestamp);
}

// --- Custom tooltip ---

function ChartTooltip({
  active,
  payload,
}: {
  active?: boolean;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  payload?: Array<{ payload: any }>;
}) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  if (!point) return null;

  // Basal data point (has `rate` field)
  if ("rate" in point && point.rate != null) {
    const time = new Date(point.timestamp).toLocaleTimeString([], {
      hour: "numeric",
      minute: "2-digit",
    });
    return (
      <div className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm shadow-lg">
        <p className="font-semibold text-blue-400">
          Basal: {point.rate.toFixed(2)} u/hr
        </p>
        <p className="text-slate-400 text-xs">{time}</p>
      </div>
    );
  }

  // Glucose data point (has `iso` and `value` fields)
  if (!point.iso || point.value == null) return null;
  const time = new Date(point.iso).toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit",
  });
  return (
    <div className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm shadow-lg">
      <p className="font-semibold" style={{ color: point.color }}>
        {point.value} mg/dL
      </p>
      <p className="text-slate-400 text-xs">{time}</p>
    </div>
  );
}

// --- Time period selector ---

interface PeriodSelectorProps {
  selected: ChartTimePeriod;
  onSelect: (p: ChartTimePeriod) => void;
}

function PeriodSelector({ selected, onSelect }: PeriodSelectorProps) {
  return (
    <div
      className="flex gap-1 bg-slate-800 rounded-lg p-1"
      role="radiogroup"
      aria-label="Time period"
    >
      {PERIODS.map(({ value, label }) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={selected === value}
          onClick={() => onSelect(value)}
          className={clsx(
            "px-3 py-1 text-sm font-medium rounded-md transition-colors",
            selected === value
              ? "bg-slate-700 text-white"
              : "text-slate-400 hover:text-slate-200"
          )}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

// --- X-axis tick formatter ---

function formatXTick(epoch: number): string {
  return new Date(epoch).toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit",
  });
}

// --- Main component ---

export interface GlucoseTrendChartProps {
  /** Signal to trigger a refetch (e.g., from SSE glucose update) */
  refreshKey?: number;
  className?: string;
  /** Dynamic glucose thresholds from user settings */
  thresholds?: {
    urgentLow: number;
    low: number;
    high: number;
    urgentHigh: number;
  };
}

export function GlucoseTrendChart({
  refreshKey,
  className,
  thresholds,
}: GlucoseTrendChartProps) {
  const { readings, isLoading, error, period, setPeriod, refetch } =
    useGlucoseHistory("3h");
  const { events: pumpEvents, refetch: refetchPump } = usePumpEvents(period);

  // Refetch when refreshKey changes (new SSE data arrived)
  const prevRefreshKeyRef = useRef(refreshKey);
  useEffect(() => {
    if (
      refreshKey !== undefined &&
      refreshKey > 0 &&
      refreshKey !== prevRefreshKeyRef.current
    ) {
      prevRefreshKeyRef.current = refreshKey;
      refetch();
      refetchPump();
    }
  }, [refreshKey, refetch, refetchPump]);

  const data = useMemo(() => transformReadings(readings, thresholds), [readings, thresholds]);
  const bolusData = useMemo(() => transformBolusEvents(pumpEvents), [pumpEvents]);
  const basalData = useMemo(() => transformBasalEvents(pumpEvents), [pumpEvents]);

  // X-axis domain: always show the full selected time window.
  // Depends on `data` so it recomputes with fresh Date.now() on refetch.
  const xDomain = useMemo(() => {
    const now = Date.now();
    return [now - PERIOD_TO_MS[period], now];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period, data]);

  // Y-axis domain: show reasonable range, expand to fit data
  const yDomain = useMemo(() => {
    if (data.length === 0) return [40, 300];
    let min = data[0].value;
    let max = data[0].value;
    for (const d of data) {
      if (d.value < min) min = d.value;
      if (d.value > max) max = d.value;
    }
    return [Math.min(40, min - 10), Math.max(300, max + 10)];
  }, [data]);

  // Insulin Y-axis domain for basal area (right side)
  const insulinDomain = useMemo(() => {
    if (basalData.length === 0) return [0, 3];
    const maxRate = basalData.reduce((m, b) => Math.max(m, b.rate), 0);
    // Scale so basal occupies roughly bottom 25% of chart
    return [0, Math.max(3, maxRate * 4)];
  }, [basalData]);

  // Loading skeleton
  if (isLoading && data.length === 0) {
    return (
      <div
        className={clsx(
          "bg-slate-900 rounded-xl p-6 border border-slate-800",
          className
        )}
        role="region"
        aria-label="Loading glucose trend chart"
        aria-busy="true"
        data-testid="glucose-trend-chart"
      >
        <div className="flex items-center justify-between mb-4">
          <div className="h-6 w-40 bg-slate-700 rounded animate-pulse" />
          <div className="h-8 w-48 bg-slate-700 rounded animate-pulse" />
        </div>
        <div className="h-64 bg-slate-800 rounded animate-pulse" />
      </div>
    );
  }

  // Error state
  if (error && data.length === 0) {
    return (
      <div
        className={clsx(
          "bg-slate-900 rounded-xl p-6 border border-slate-800",
          className
        )}
        role="region"
        aria-label="Glucose trend chart"
        data-testid="glucose-trend-chart"
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-slate-200">
            Glucose Trend
          </h2>
          <PeriodSelector selected={period} onSelect={setPeriod} />
        </div>
        <div className="h-64 flex flex-col items-center justify-center text-slate-500 gap-3">
          <p>Unable to load glucose history</p>
          <button
            type="button"
            onClick={refetch}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-slate-800 text-slate-300 hover:bg-slate-700 hover:text-white transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  // Empty state
  if (data.length === 0) {
    return (
      <div
        className={clsx(
          "bg-slate-900 rounded-xl p-6 border border-slate-800",
          className
        )}
        role="region"
        aria-label="Glucose trend chart"
        data-testid="glucose-trend-chart"
      >
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-slate-200">
            Glucose Trend
          </h2>
          <PeriodSelector selected={period} onSelect={setPeriod} />
        </div>
        <div className="h-64 flex items-center justify-center text-slate-500">
          <p>No glucose readings yet</p>
        </div>
      </div>
    );
  }

  const lowThreshold = thresholds?.low ?? GLUCOSE_THRESHOLDS.LOW;
  const highThreshold = thresholds?.high ?? GLUCOSE_THRESHOLDS.HIGH;
  const targetLabel = `${lowThreshold}-${highThreshold} Target`;

  return (
    <div
      className={clsx(
        "bg-slate-900 rounded-xl p-6 border border-slate-800",
        className
      )}
      role="region"
      aria-label={`Glucose trend chart, ${period} view`}
      data-testid="glucose-trend-chart"
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold text-slate-200">Glucose Trend</h2>
        <PeriodSelector selected={period} onSelect={setPeriod} />
      </div>

      {/* Chart */}
      <div className="h-64 md:h-72 lg:h-80">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart
            margin={{ top: 10, right: 10, bottom: 0, left: -10 }}
          >
            <CartesianGrid
              stroke="#334155"
              strokeDasharray="3 3"
              vertical={false}
            />

            {/* Target range band */}
            <ReferenceArea
              yAxisId="glucose"
              y1={lowThreshold}
              y2={highThreshold}
              fill="#22c55e"
              fillOpacity={0.08}
              stroke="none"
            />

            <XAxis
              dataKey="timestamp"
              type="number"
              domain={xDomain}
              tickFormatter={formatXTick}
              tick={{ fill: "#94a3b8", fontSize: 12 }}
              axisLine={{ stroke: "#475569" }}
              tickLine={{ stroke: "#475569" }}
              allowDuplicatedCategory={false}
            />
            <YAxis
              yAxisId="glucose"
              dataKey="value"
              type="number"
              domain={yDomain}
              tick={{ fill: "#94a3b8", fontSize: 12 }}
              axisLine={{ stroke: "#475569" }}
              tickLine={{ stroke: "#475569" }}
            />
            <YAxis
              yAxisId="insulin"
              orientation="right"
              domain={insulinDomain}
              hide
            />

            {/* Basal rate area (bottom portion of chart) */}
            {basalData.length > 0 && (
              <Area
                yAxisId="insulin"
                data={basalData}
                dataKey="rate"
                type="stepAfter"
                fill="rgba(59,130,246,0.15)"
                stroke="rgb(59,130,246)"
                strokeWidth={1}
                dot={false}
                isAnimationActive={false}
              />
            )}

            {/* Glucose scatter points */}
            <Scatter yAxisId="glucose" data={data} shape="circle" isAnimationActive={false}>
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} r={4} />
              ))}
            </Scatter>

            {/* Bolus delivery markers */}
            {bolusData.map((b, i) => (
              <ReferenceLine
                key={`bolus-${b.timestamp}-${i}`}
                yAxisId="glucose"
                x={b.timestamp}
                stroke={b.isCorrection ? "#3b82f6" : "#8b5cf6"}
                strokeDasharray="4 3"
                strokeWidth={1.5}
                label={{
                  value: b.label,
                  position: "top",
                  fill: b.isCorrection ? "#3b82f6" : "#8b5cf6",
                  fontSize: 10,
                  fontWeight: 600,
                }}
              />
            ))}

            <Tooltip
              content={<ChartTooltip />}
              cursor={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap items-center justify-center gap-4 mt-3 text-xs text-slate-500">
        <span className="flex items-center gap-1">
          <span
            className="w-2 h-2 rounded-full bg-green-500 inline-block"
            aria-hidden="true"
          />
          {targetLabel}
        </span>
        <span className="flex items-center gap-1">
          <span
            className="w-2 h-2 rounded-full bg-amber-500 inline-block"
            aria-hidden="true"
          />
          High/Low
        </span>
        <span className="flex items-center gap-1">
          <span
            className="w-2 h-2 rounded-full bg-red-600 inline-block"
            aria-hidden="true"
          />
          Urgent
        </span>
        {bolusData.length > 0 && (
          <span className="flex items-center gap-1">
            <span
              className="w-3 h-0 border-t-2 border-dashed border-violet-500 inline-block"
              aria-hidden="true"
            />
            Bolus
          </span>
        )}
        {basalData.length > 0 && (
          <span className="flex items-center gap-1">
            <span
              className="w-3 h-2 bg-blue-500/20 border border-blue-500 inline-block"
              aria-hidden="true"
            />
            Basal
          </span>
        )}
      </div>
    </div>
  );
}

export default GlucoseTrendChart;
