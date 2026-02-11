"use client";

/**
 * GlucoseTrendChart Component
 *
 * Dexcom-style glucose trend chart with colored dots, target range band,
 * and time period selector (3H, 6H, 12H, 24H).
 */

import { useMemo, useEffect, useRef } from "react";
import {
  ResponsiveContainer,
  ScatterChart,
  Scatter,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
  Cell,
} from "recharts";
import clsx from "clsx";
import { type GlucoseHistoryReading } from "@/lib/api";
import { GLUCOSE_THRESHOLDS } from "./glucose-hero";
import {
  type ChartTimePeriod,
  useGlucoseHistory,
} from "@/hooks/use-glucose-history";

// --- Color mapping by glucose classification ---

function getPointColor(value: number): string {
  if (value < GLUCOSE_THRESHOLDS.URGENT_LOW) return "#dc2626"; // red-600
  if (value < GLUCOSE_THRESHOLDS.LOW) return "#f59e0b"; // amber-500
  if (value <= GLUCOSE_THRESHOLDS.HIGH) return "#22c55e"; // green-500
  if (value <= GLUCOSE_THRESHOLDS.URGENT_HIGH) return "#f59e0b"; // amber-500
  return "#dc2626"; // red-600
}

// --- Time period buttons ---

const PERIODS: { value: ChartTimePeriod; label: string }[] = [
  { value: "3h", label: "3H" },
  { value: "6h", label: "6H" },
  { value: "12h", label: "12H" },
  { value: "24h", label: "24H" },
];

// --- Chart data point ---

interface ChartPoint {
  timestamp: number;
  value: number;
  color: string;
  iso: string;
}

function transformReadings(readings: GlucoseHistoryReading[]): ChartPoint[] {
  return readings
    .map((r) => ({
      timestamp: new Date(r.reading_timestamp).getTime(),
      value: r.value,
      color: getPointColor(r.value),
      iso: r.reading_timestamp,
    }))
    .sort((a, b) => a.timestamp - b.timestamp);
}

// --- Custom tooltip ---

function ChartTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload: ChartPoint }>;
}) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
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
}

export function GlucoseTrendChart({
  refreshKey,
  className,
}: GlucoseTrendChartProps) {
  const { readings, isLoading, error, period, setPeriod, refetch } =
    useGlucoseHistory("3h");

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
    }
  }, [refreshKey, refetch]);

  const data = useMemo(() => transformReadings(readings), [readings]);

  // Y-axis domain: show reasonable range, expand to fit data
  const yDomain = useMemo(() => {
    if (data.length === 0) return [40, 300];
    const values = data.map((d) => d.value);
    const min = Math.min(...values);
    const max = Math.max(...values);
    return [Math.min(40, min - 10), Math.max(300, max + 10)];
  }, [data]);

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
        <div className="h-64 flex items-center justify-center text-slate-500">
          <p>Unable to load glucose history</p>
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
          <ScatterChart
            margin={{ top: 10, right: 10, bottom: 0, left: -10 }}
          >
            <CartesianGrid
              stroke="#334155"
              strokeDasharray="3 3"
              vertical={false}
            />

            {/* Target range band (70-180) */}
            <ReferenceArea
              y1={GLUCOSE_THRESHOLDS.LOW}
              y2={GLUCOSE_THRESHOLDS.HIGH}
              fill="#22c55e"
              fillOpacity={0.08}
              stroke="none"
            />

            <XAxis
              dataKey="timestamp"
              type="number"
              domain={["dataMin", "dataMax"]}
              tickFormatter={formatXTick}
              tick={{ fill: "#94a3b8", fontSize: 12 }}
              axisLine={{ stroke: "#475569" }}
              tickLine={{ stroke: "#475569" }}
            />
            <YAxis
              dataKey="value"
              type="number"
              domain={yDomain}
              tick={{ fill: "#94a3b8", fontSize: 12 }}
              axisLine={{ stroke: "#475569" }}
              tickLine={{ stroke: "#475569" }}
            />
            <Tooltip
              content={<ChartTooltip />}
              cursor={false}
            />
            <Scatter data={data} shape="circle">
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} r={4} />
              ))}
            </Scatter>
          </ScatterChart>
        </ResponsiveContainer>
      </div>

      {/* Range legend */}
      <div className="flex items-center justify-center gap-4 mt-3 text-xs text-slate-500">
        <span className="flex items-center gap-1">
          <span
            className="w-2 h-2 rounded-full bg-green-500 inline-block"
            aria-hidden="true"
          />
          70-180 Target
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
      </div>
    </div>
  );
}

export default GlucoseTrendChart;
