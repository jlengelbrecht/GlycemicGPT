"use client";

/**
 * Clinical Report Page
 *
 * Dedicated full-page report similar to Tandem t:connect or Dexcom Clarity.
 * Date range controls are at the top (hidden on print). The report body
 * renders below -- on screen it lives inside the dashboard layout (sidebar
 * visible) but the existing globals.css print rules hide sidebar/header
 * automatically, producing a clean printable document.
 */

import { useState, useEffect, useMemo, useCallback, useRef } from "react";
import {
  Loader2,
  AlertCircle,
  Printer,
  FileText,
  Calendar,
  ArrowLeft,
} from "lucide-react";
import {
  ScatterChart,
  Scatter,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ReferenceLine,
  ReferenceArea,
  ResponsiveContainer,
} from "recharts";
import Link from "next/link";
import {
  getGlucoseHistoryByDateRange,
  getGlucoseStatsByDateRange,
  getTimeInRangeDetailByDateRange,
  getInsulinSummaryByDateRange,
  getBolusReviewByDateRange,
  type GlucoseHistoryReading,
  type GlucoseStats,
  type TimeInRangeDetailStats,
  type TirBucket,
  type InsulinSummaryResponse,
  type BolusReviewItem,
} from "@/lib/api";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ReportData {
  readings: GlucoseHistoryReading[];
  cgmStats: GlucoseStats | null;
  tirStats: TimeInRangeDetailStats | null;
  insulin: InsulinSummaryResponse | null;
  boluses: BolusReviewItem[];
  totalBolusCount: number;
  warnings: string[];
}

// ---------------------------------------------------------------------------
// Date helpers
// ---------------------------------------------------------------------------

function todayDateString(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function daysAgoDateString(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function formatDisplayDate(date: string): string {
  const d = new Date(`${date}T12:00:00`);
  return d.toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function daysBetween(start: string, end: string): number {
  const s = new Date(`${start}T00:00:00`);
  const e = new Date(`${end}T00:00:00`);
  return Math.round((e.getTime() - s.getTime()) / 86400000);
}

// ---------------------------------------------------------------------------
// Data fetching
// ---------------------------------------------------------------------------

const SECTION_NAMES = [
  "Glucose History",
  "CGM Stats",
  "Time in Range",
  "Insulin Summary",
  "Bolus Events",
] as const;

async function fetchReportData(
  startDate: string,
  endDate: string,
): Promise<ReportData> {
  const startISO = `${startDate}T00:00:00Z`;
  const nextDay = new Date(`${endDate}T00:00:00Z`);
  nextDay.setUTCDate(nextDay.getUTCDate() + 1);
  const endISO = nextDay.toISOString();

  const results = await Promise.allSettled([
    getGlucoseHistoryByDateRange(startISO, endISO, 8640),
    getGlucoseStatsByDateRange(startISO, endISO),
    getTimeInRangeDetailByDateRange(startISO, endISO),
    getInsulinSummaryByDateRange(startISO, endISO),
    getBolusReviewByDateRange(startISO, endISO, 50),
  ]);

  const warnings: string[] = [];
  results.forEach((r, i) => {
    if (r.status === "rejected") {
      warnings.push(`${SECTION_NAMES[i]} data could not be loaded`);
    }
  });

  const bolusResult = results[4].status === "fulfilled" ? results[4].value : null;

  return {
    readings:
      results[0].status === "fulfilled" ? results[0].value.readings : [],
    cgmStats: results[1].status === "fulfilled" ? results[1].value : null,
    tirStats: results[2].status === "fulfilled" ? results[2].value : null,
    insulin: results[3].status === "fulfilled" ? results[3].value : null,
    boluses: bolusResult?.boluses ?? [],
    totalBolusCount: bolusResult?.total_count ?? 0,
    warnings,
  };
}

// ---------------------------------------------------------------------------
// Chart components
// ---------------------------------------------------------------------------

interface ChartPoint {
  time: number;
  value: number;
  timestamp: string;
}

function transformReadings(readings: GlucoseHistoryReading[]): ChartPoint[] {
  return readings
    .map((r) => ({
      time: new Date(r.reading_timestamp).getTime(),
      value: r.value,
      timestamp: r.reading_timestamp,
    }))
    .sort((a, b) => a.time - b.time);
}

function getPointColor(value: number, low: number, high: number): string {
  if (value < 54) return "#dc2626";
  if (value < low) return "#f59e0b";
  if (value > 300) return "#dc2626";
  if (value > high) return "#f97316";
  return "#22c55e";
}

function GlucoseChartTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload: ChartPoint }>;
}) {
  if (!active || !payload?.[0]) return null;
  const point = payload[0].payload;
  const time = new Date(point.timestamp).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
  return (
    <div className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 shadow-lg print:hidden">
      <p className="text-xs text-slate-500 dark:text-slate-400">{time}</p>
      <p className="text-sm font-semibold text-slate-900 dark:text-white">
        {point.value} mg/dL
      </p>
    </div>
  );
}

function makeScatterRenderer(low: number, high: number) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return function renderScatterPoint(props: any) {
    const { cx, cy, payload } = props;
    if (cx == null || cy == null) return null;
    const value = (payload as ChartPoint | undefined)?.value ?? 100;
    return (
      <circle
        cx={cx}
        cy={cy}
        r={2}
        fill={getPointColor(value, low, high)}
        opacity={0.7}
      />
    );
  };
}

function ReportGlucoseChart({
  readings,
  startDate,
  endDate,
  low = 70,
  high = 180,
}: {
  readings: GlucoseHistoryReading[];
  startDate: string;
  endDate: string;
  low?: number;
  high?: number;
}) {
  const points = useMemo(() => transformReadings(readings), [readings]);
  const renderPoint = useMemo(
    () => makeScatterRenderer(low, high),
    [low, high],
  );

  const domainStart = new Date(`${startDate}T00:00:00`).getTime();
  const domainEnd =
    new Date(`${endDate}T00:00:00`).getTime() + 24 * 60 * 60 * 1000;
  const days = daysBetween(startDate, endDate);

  const ticks = useMemo(() => {
    const result: number[] = [];
    const step = days <= 7 ? 1 : days <= 14 ? 2 : days <= 21 ? 3 : 5;
    const d = new Date(`${startDate}T00:00:00`);
    while (d.getTime() <= domainEnd) {
      result.push(d.getTime());
      d.setDate(d.getDate() + step);
    }
    return result;
  }, [startDate, domainEnd, days]);

  const chartSummary = useMemo(() => {
    if (points.length === 0) return "";
    const avg = Math.round(points.reduce((s, p) => s + p.value, 0) / points.length);
    const startLabel = new Date(domainStart).toLocaleDateString([], { month: "short", day: "numeric" });
    const endLabel = new Date(domainEnd).toLocaleDateString([], { month: "short", day: "numeric" });
    return `Glucose trend chart showing ${points.length} readings from ${startLabel} to ${endLabel}, average ${avg} mg/dL`;
  }, [points, domainStart, domainEnd]);

  if (points.length === 0) {
    return (
      <div className="flex items-center justify-center h-48 text-slate-500 text-sm">
        No glucose data for this period.
      </div>
    );
  }

  return (
    <div role="img" aria-label={chartSummary}>
    <ResponsiveContainer width="100%" height={280}>
      <ScatterChart margin={{ top: 10, right: 20, bottom: 20, left: 10 }}>
        <CartesianGrid
          strokeDasharray="3 3"
          stroke="#e2e8f0"
          opacity={0.5}
        />
        <XAxis
          type="number"
          dataKey="time"
          domain={[domainStart, domainEnd]}
          ticks={ticks}
          tickFormatter={(ts: number) => {
            const d = new Date(ts);
            return d.toLocaleDateString([], { month: "short", day: "numeric" });
          }}
          tick={{ fontSize: 10, fill: "#94a3b8" }}
        />
        <YAxis
          type="number"
          dataKey="value"
          domain={[40, 350]}
          ticks={[54, low, high, 250, 300]}
          tick={{ fontSize: 10, fill: "#94a3b8" }}
          width={35}
        />
        <ReferenceArea y1={low} y2={high} fill="#22c55e" fillOpacity={0.08} />
        <ReferenceLine
          y={low}
          stroke="#f59e0b"
          strokeDasharray="4 4"
          strokeOpacity={0.6}
        />
        <ReferenceLine
          y={high}
          stroke="#f97316"
          strokeDasharray="4 4"
          strokeOpacity={0.6}
        />
        <RechartsTooltip content={<GlucoseChartTooltip />} cursor={false} />
        <Scatter data={points} shape={renderPoint} />
      </ScatterChart>
    </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Report sections
// ---------------------------------------------------------------------------

const TIR_COLORS: Record<string, string> = {
  urgent_low: "#dc2626",
  low: "#f59e0b",
  in_range: "#22c55e",
  high: "#f97316",
  urgent_high: "#ef4444",
};

const TIR_LABELS: Record<string, string> = {
  urgent_low: "Very Low",
  low: "Low",
  in_range: "In Range",
  high: "High",
  urgent_high: "Very High",
};

const TIR_TARGETS: Record<string, string> = {
  urgent_low: "<1%",
  low: "<4%",
  in_range: ">70%",
  high: "<25%",
  urgent_high: "<5%",
};

function CgmStatsTable({ stats }: { stats: GlucoseStats }) {
  const rows = [
    {
      label: "Average Glucose",
      value: `${Math.round(stats.mean_glucose)} mg/dL`,
    },
    { label: "Standard Deviation", value: `${Math.round(stats.std_dev)} mg/dL` },
    {
      label: "Coefficient of Variation",
      value: `${stats.cv_pct}%`,
      note: stats.cv_pct < 36 ? "Stable" : "Variable",
    },
    { label: "Glucose Management Indicator", value: `${stats.gmi}%` },
    { label: "CGM Active Time", value: `${stats.cgm_active_pct}%` },
    { label: "Total Readings", value: `${stats.readings_count.toLocaleString()}` },
  ];

  return (
    <table className="w-full text-sm">
      <tbody>
        {rows.map((row) => (
          <tr
            key={row.label}
            className="border-b border-slate-200 dark:border-slate-700 print:border-slate-300"
          >
            <td className="py-2 text-slate-600 dark:text-slate-400 print:text-slate-600">
              {row.label}
            </td>
            <td className="py-2 text-right font-medium text-slate-900 dark:text-white print:text-black">
              {row.value}
              {row.note && (
                <span className="ml-2 text-xs text-slate-400 print:text-slate-500">
                  ({row.note})
                </span>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TirSection({ tir }: { tir: TimeInRangeDetailStats }) {
  const t = tir.thresholds;
  const orderedLabels = [
    "urgent_low",
    "low",
    "in_range",
    "high",
    "urgent_high",
  ] as const;
  const orderedBuckets = orderedLabels
    .map((label) => tir.buckets.find((b) => b.label === label))
    .filter((b): b is TirBucket => !!b);

  function rangeLabel(bucket: string): string {
    switch (bucket) {
      case "urgent_low":
        return `<${t.urgent_low}`;
      case "low":
        return `${t.urgent_low}-${t.low}`;
      case "in_range":
        return `${t.low}-${t.high}`;
      case "high":
        return `${t.high}-${t.urgent_high}`;
      case "urgent_high":
        return `>${t.urgent_high}`;
      default:
        return "";
    }
  }

  return (
    <div className="space-y-4">
      <div
        className="flex h-8 rounded-full overflow-hidden"
        role="img"
        aria-label="Time in range distribution"
      >
        {orderedBuckets.map((bucket) =>
          bucket.pct > 0 ? (
            <div
              key={bucket.label}
              style={{
                width: `${bucket.pct}%`,
                backgroundColor: TIR_COLORS[bucket.label],
              }}
              title={`${TIR_LABELS[bucket.label]}: ${bucket.pct}%`}
            />
          ) : null,
        )}
      </div>

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b-2 border-slate-300 dark:border-slate-600 print:border-slate-400">
            <th className="py-1.5 text-left text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
              Range
            </th>
            <th className="py-1.5 text-left text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
              mg/dL
            </th>
            <th className="py-1.5 text-right text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
              % Time
            </th>
            <th className="py-1.5 text-right text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
              Target
            </th>
          </tr>
        </thead>
        <tbody>
          {orderedBuckets.map((bucket) => {
            const isTarget =
              bucket.label === "in_range"
                ? bucket.pct >= 70
                : bucket.label === "urgent_low"
                  ? bucket.pct < 1
                  : bucket.label === "low"
                    ? bucket.pct < 4
                    : bucket.label === "high"
                      ? bucket.pct < 25
                      : bucket.pct < 5;
            return (
              <tr
                key={bucket.label}
                className="border-b border-slate-200 dark:border-slate-700 print:border-slate-300"
              >
                <td className="py-2 flex items-center gap-2">
                  <span
                    className="inline-block w-3 h-3 rounded-sm"
                    style={{ backgroundColor: TIR_COLORS[bucket.label] }}
                  />
                  <span className="text-slate-700 dark:text-slate-300 print:text-slate-700">
                    {TIR_LABELS[bucket.label]}
                  </span>
                </td>
                <td className="py-2 text-slate-500 print:text-slate-500">
                  {rangeLabel(bucket.label)}
                </td>
                <td className="py-2 text-right font-semibold text-slate-900 dark:text-white print:text-black">
                  {bucket.pct}%
                </td>
                <td className="py-2 text-right">
                  <span
                    className={
                      isTarget
                        ? "text-green-600 print:text-green-700"
                        : "text-red-500 print:text-red-600"
                    }
                  >
                    {TIR_TARGETS[bucket.label]}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="text-xs text-slate-400 print:text-slate-500">
        Based on {tir.readings_count.toLocaleString()} readings. Clinical
        targets per International Consensus (Battelino et al. 2019).
      </p>
    </div>
  );
}

function InsulinSection({ insulin }: { insulin: InsulinSummaryResponse }) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div>
          <p className="text-xs text-slate-500 print:text-slate-600 mb-1">
            Total Daily Dose (avg)
          </p>
          <p className="text-xl font-bold text-slate-900 dark:text-white print:text-black">
            {insulin.tdd.toFixed(1)}{" "}
            <span className="text-sm font-normal">U</span>
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-500 print:text-slate-600 mb-1">
            Basal (avg/day)
          </p>
          <p className="text-xl font-bold text-slate-900 dark:text-white print:text-black">
            {insulin.basal_units.toFixed(1)} U
            <span className="text-sm font-normal text-slate-400 ml-1">
              ({insulin.basal_pct}%)
            </span>
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-500 print:text-slate-600 mb-1">
            Bolus (avg/day)
          </p>
          <p className="text-xl font-bold text-slate-900 dark:text-white print:text-black">
            {insulin.bolus_units.toFixed(1)} U
            <span className="text-sm font-normal text-slate-400 ml-1">
              ({insulin.bolus_pct}%)
            </span>
          </p>
        </div>
        <div>
          <p className="text-xs text-slate-500 print:text-slate-600 mb-1">
            Boluses (total)
          </p>
          <p className="text-xl font-bold text-slate-900 dark:text-white print:text-black">
            {insulin.bolus_count}
            {insulin.correction_count > 0 && (
              <span className="text-sm font-normal text-slate-400 ml-1">
                ({insulin.correction_count} auto)
              </span>
            )}
          </p>
        </div>
      </div>
      <div className="flex h-4 rounded-full overflow-hidden">
        <div
          style={{
            width: `${insulin.basal_pct}%`,
            backgroundColor: "#6366f1",
          }}
          title={`Basal: ${insulin.basal_pct}%`}
        />
        <div
          style={{
            width: `${insulin.bolus_pct}%`,
            backgroundColor: "#3b82f6",
          }}
          title={`Bolus: ${insulin.bolus_pct}%`}
        />
      </div>
      <div className="flex gap-4 text-xs">
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 rounded-sm bg-indigo-500" />
          <span className="text-slate-600 dark:text-slate-300 print:text-slate-600">
            Basal {insulin.basal_pct}%
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 rounded-sm bg-blue-500" />
          <span className="text-slate-600 dark:text-slate-300 print:text-slate-600">
            Bolus {insulin.bolus_pct}%
          </span>
        </div>
      </div>
      <p className="text-xs text-slate-400 print:text-slate-500">
        Daily averages over {insulin.period_days} day
        {insulin.period_days !== 1 ? "s" : ""}.
      </p>
    </div>
  );
}

function BolusTable({ boluses, totalCount }: { boluses: BolusReviewItem[]; totalCount: number }) {
  if (boluses.length === 0) {
    return (
      <p className="text-sm text-slate-500">No bolus events for this period.</p>
    );
  }

  return (
    <div className="space-y-2">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b-2 border-slate-300 dark:border-slate-600 print:border-slate-400">
              <th className="py-1.5 px-2 text-left text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
                Date/Time
              </th>
              <th className="py-1.5 px-2 text-right text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
                Units
              </th>
              <th className="py-1.5 px-2 text-center text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
                Type
              </th>
              <th className="py-1.5 px-2 text-right text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
                BG
              </th>
              <th className="py-1.5 px-2 text-right text-xs font-semibold text-slate-500 print:text-slate-600 uppercase tracking-wider">
                IoB
              </th>
            </tr>
          </thead>
          <tbody>
            {boluses.map((b, i) => (
              <tr
                key={`${b.event_timestamp}-${i}`}
                className="border-b border-slate-200 dark:border-slate-700 print:border-slate-300"
              >
                <td className="py-1.5 px-2 text-slate-600 dark:text-slate-300 print:text-slate-700 whitespace-nowrap">
                  {new Date(b.event_timestamp).toLocaleString([], {
                    month: "short",
                    day: "numeric",
                    hour: "numeric",
                    minute: "2-digit",
                  })}
                </td>
                <td className="py-1.5 px-2 text-right font-medium text-slate-900 dark:text-white print:text-black">
                  {b.units.toFixed(2)} U
                </td>
                <td className="py-1.5 px-2 text-center">
                  {b.is_automated ? (
                    <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-violet-100 text-violet-700 print:bg-violet-50 print:text-violet-800">
                      Auto
                    </span>
                  ) : (
                    <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-600 print:bg-slate-50 print:text-slate-700">
                      Manual
                    </span>
                  )}
                </td>
                <td className="py-1.5 px-2 text-right text-slate-600 dark:text-slate-300 print:text-slate-700">
                  {b.bg_at_event != null
                    ? `${Math.round(b.bg_at_event)}`
                    : "---"}
                </td>
                <td className="py-1.5 px-2 text-right text-slate-600 dark:text-slate-300 print:text-slate-700">
                  {b.iob_at_event != null
                    ? `${b.iob_at_event.toFixed(1)} U`
                    : "---"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalCount > boluses.length && (
        <p className="text-xs text-slate-400 print:text-slate-500">
          Showing most recent {boluses.length} of {totalCount} bolus events.
        </p>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Page component
// ---------------------------------------------------------------------------

const PRESETS = [
  { label: "7 Days", days: 7 },
  { label: "14 Days", days: 14 },
  { label: "30 Days", days: 30 },
];

export default function ClinicalReportPage() {
  const [startDate, setStartDate] = useState(daysAgoDateString(14));
  const [endDate, setEndDate] = useState(todayDateString());
  const [selectedPreset, setSelectedPreset] = useState<number | null>(14);
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reportData, setReportData] = useState<ReportData | null>(null);
  const [generatedAt, setGeneratedAt] = useState<string | null>(null);
  const [reportStartDate, setReportStartDate] = useState<string | null>(null);
  const [reportEndDate, setReportEndDate] = useState<string | null>(null);
  const didAutoGenerate = useRef(false);
  const requestIdRef = useRef(0);

  const numDays = useMemo(
    () => daysBetween(startDate, endDate),
    [startDate, endDate],
  );
  const isValid = numDays >= 1 && numDays <= 31;
  const reportDays = reportStartDate && reportEndDate
    ? daysBetween(reportStartDate, reportEndDate)
    : 0;

  const handlePreset = useCallback((days: number) => {
    setStartDate(daysAgoDateString(days));
    setEndDate(todayDateString());
    setSelectedPreset(days);
  }, []);

  const handleGenerate = useCallback(async () => {
    if (!isValid) return;
    const currentRequestId = ++requestIdRef.current;
    setIsGenerating(true);
    setError(null);
    setReportData(null);
    try {
      const data = await fetchReportData(startDate, endDate);
      if (currentRequestId !== requestIdRef.current) return;
      setReportData(data);
      setReportStartDate(startDate);
      setReportEndDate(endDate);
      setGeneratedAt(
        new Date().toLocaleString([], {
          year: "numeric",
          month: "long",
          day: "numeric",
          hour: "numeric",
          minute: "2-digit",
        }),
      );
    } catch (err) {
      if (currentRequestId !== requestIdRef.current) return;
      setError(
        err instanceof Error ? err.message : "Failed to generate report",
      );
    } finally {
      if (currentRequestId === requestIdRef.current) {
        setIsGenerating(false);
      }
    }
  }, [startDate, endDate, isValid]);

  const handlePrint = useCallback(() => {
    window.print();
  }, []);

  // Auto-generate on first load with default 14-day range
  useEffect(() => {
    if (didAutoGenerate.current) return;
    didAutoGenerate.current = true;
    let cancelled = false;
    const start = daysAgoDateString(14);
    const end = todayDateString();
    setIsGenerating(true);
    fetchReportData(start, end)
      .then((data) => {
        if (cancelled) return;
        setReportData(data);
        setReportStartDate(start);
        setReportEndDate(end);
        setGeneratedAt(
          new Date().toLocaleString([], {
            year: "numeric",
            month: "long",
            day: "numeric",
            hour: "numeric",
            minute: "2-digit",
          }),
        );
      })
      .catch((err) => {
        if (cancelled) return;
        setError(
          err instanceof Error ? err.message : "Failed to generate report",
        );
      })
      .finally(() => {
        if (!cancelled) {
          setIsGenerating(false);
        }
      });
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="space-y-6">
      {/* Controls bar -- hidden on print */}
      <div className="print:hidden">
        <Link
          href="/dashboard/settings/data"
          className="inline-flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-200 mb-4"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Data Settings
        </Link>

        <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 bg-blue-500/10 rounded-lg">
              <FileText className="h-5 w-5 text-blue-400" />
            </div>
            <div>
              <h1 className="text-lg font-semibold text-slate-900 dark:text-white">
                Clinical Report
              </h1>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                Generate a printable report for your healthcare provider
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-end gap-4">
            {/* Presets */}
            <div className="flex gap-2">
              {PRESETS.map((preset) => (
                <button
                  key={preset.days}
                  type="button"
                  onClick={() => handlePreset(preset.days)}
                  className={`px-3 py-1.5 text-sm rounded-lg border transition-colors ${
                    selectedPreset === preset.days
                      ? "border-blue-500 bg-blue-500/10 text-blue-600 dark:text-blue-400"
                      : "border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>

            {/* Date range */}
            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-slate-400" />
              <input
                id="report-start"
                type="date"
                aria-label="Report start date"
                value={startDate}
                max={endDate}
                onChange={(e) => {
                  if (e.target.value) {
                    setStartDate(e.target.value);
                    setSelectedPreset(null);
                  }
                }}
                className="px-3 py-1.5 text-sm rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
              />
              <span className="text-sm text-slate-400">to</span>
              <input
                id="report-end"
                type="date"
                aria-label="Report end date"
                value={endDate}
                min={startDate}
                max={todayDateString()}
                onChange={(e) => {
                  if (e.target.value) {
                    setEndDate(e.target.value);
                    setSelectedPreset(null);
                  }
                }}
                className="px-3 py-1.5 text-sm rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
              />
              <span className="text-xs text-slate-400">
                {numDays}d
                {!isValid && numDays > 31 && (
                  <span className="text-red-400 ml-1">(max 31)</span>
                )}
              </span>
            </div>

            {/* Generate */}
            <button
              type="button"
              onClick={handleGenerate}
              disabled={!isValid || isGenerating}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-600/50 text-white text-sm font-medium rounded-lg transition-colors disabled:cursor-not-allowed"
            >
              {isGenerating ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <FileText className="h-4 w-4" />
              )}
              {isGenerating ? "Generating..." : "Generate Report"}
            </button>

            {/* Print */}
            {reportData && (
              <button
                type="button"
                onClick={handlePrint}
                className="inline-flex items-center gap-2 px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-lg transition-colors"
              >
                <Printer className="h-4 w-4" />
                Print / Save PDF
              </button>
            )}
          </div>

          {error && (
            <div className="flex items-center gap-2 text-sm text-red-400 mt-3">
              <AlertCircle className="h-4 w-4" />
              {error}
            </div>
          )}
        </div>
      </div>

      {/* Loading state */}
      {isGenerating && !reportData && (
        <div className="flex flex-col items-center justify-center py-20 print:hidden">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-3" />
          <p className="text-sm text-slate-400">Generating your report...</p>
        </div>
      )}

      {/* Report body -- this is what prints */}
      {reportData && reportStartDate && reportEndDate && (
        <div className="space-y-4 print:space-y-2">
          {/* Report header */}
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 print:border-0 print:rounded-none print:p-0 print:pb-4 print:mb-2 print:break-inside-avoid">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white print:text-black print:text-2xl">
              GlycemicGPT Clinical Report
            </h2>
            <p className="text-sm text-slate-500 print:text-slate-600 mt-1">
              {formatDisplayDate(reportStartDate)} &ndash;{" "}
              {formatDisplayDate(reportEndDate)} ({reportDays} day
              {reportDays !== 1 ? "s" : ""})
            </p>
            <p className="text-xs text-slate-400 print:text-slate-500 mt-0.5">
              Generated {generatedAt}
            </p>
          </div>

          {/* Partial failure warnings */}
          {reportData.warnings.length > 0 && (
            <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-300 dark:border-yellow-700 rounded-xl p-4 print:border print:border-yellow-400 print:bg-yellow-50">
              <div className="flex items-start gap-2">
                <AlertCircle className="h-4 w-4 text-yellow-600 dark:text-yellow-400 mt-0.5 shrink-0" />
                <div>
                  <p className="text-sm font-medium text-yellow-800 dark:text-yellow-300">
                    Incomplete Report
                  </p>
                  <ul className="text-xs text-yellow-700 dark:text-yellow-400 mt-1 list-disc list-inside">
                    {reportData.warnings.map((w) => (
                      <li key={w}>{w}</li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
          )}

          {/* CGM Summary */}
          {reportData.cgmStats &&
            reportData.cgmStats.readings_count > 0 && (
              <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 print:border-0 print:rounded-none print:border-b print:border-slate-300 print:p-4 print:break-inside-avoid">
                <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 print:text-slate-600 uppercase tracking-wider mb-3">
                  CGM Summary
                </h3>
                <CgmStatsTable stats={reportData.cgmStats} />
              </div>
            )}

          {/* Time in Range */}
          {reportData.tirStats &&
            reportData.tirStats.readings_count > 0 && (
              <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 print:border-0 print:rounded-none print:border-b print:border-slate-300 print:p-4 print:break-inside-avoid">
                <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 print:text-slate-600 uppercase tracking-wider mb-3">
                  Time in Range
                </h3>
                <TirSection tir={reportData.tirStats} />
              </div>
            )}

          {/* Glucose Trend */}
          {reportData.readings.length > 0 && (
            <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 print:border-0 print:rounded-none print:border-b print:border-slate-300 print:p-4 print:break-inside-avoid">
              <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 print:text-slate-600 uppercase tracking-wider mb-3">
                Glucose Trend
              </h3>
              <ReportGlucoseChart
                readings={reportData.readings}
                startDate={reportStartDate}
                endDate={reportEndDate}
                low={reportData.tirStats?.thresholds.low}
                high={reportData.tirStats?.thresholds.high}
              />
            </div>
          )}

          {/* Insulin Delivery */}
          {reportData.insulin &&
            (reportData.insulin.tdd > 0 ||
              reportData.insulin.bolus_count > 0) && (
              <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 print:border-0 print:rounded-none print:border-b print:border-slate-300 print:p-4 print:break-inside-avoid">
                <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 print:text-slate-600 uppercase tracking-wider mb-3">
                  Insulin Delivery
                </h3>
                <InsulinSection insulin={reportData.insulin} />
              </div>
            )}

          {/* Bolus Events */}
          {reportData.boluses.length > 0 && (
            <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 print:border-0 print:rounded-none print:border-b print:border-slate-300 print:p-4 print:break-inside-avoid">
              <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 print:text-slate-600 uppercase tracking-wider mb-3">
                Bolus Events
              </h3>
              <BolusTable boluses={reportData.boluses} totalCount={reportData.totalBolusCount} />
            </div>
          )}

          {/* Footer */}
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 print:border-0 print:rounded-none print:p-2 print:mt-4">
            <p className="text-xs text-slate-400 print:text-slate-500 text-center">
              This report is generated from data collected by GlycemicGPT and is
              intended for informational purposes only. It is not a substitute
              for professional medical advice, diagnosis, or treatment. Always
              consult with a qualified healthcare provider.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
