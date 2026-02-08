"use client";

/**
 * TimeInRangeBar Component
 *
 * Story 4.4: Time in Range Bar Component
 * Displays a horizontal stacked bar showing the percentage of time
 * spent in different glucose ranges (low, in-range, high).
 */

import { motion, useReducedMotion } from "framer-motion";
import clsx from "clsx";

/** Time period options for the display */
export type TimePeriod = "24h" | "7d" | "14d" | "30d" | "90d";

/** Data for each glucose range segment */
export interface RangeData {
  /** Percentage of time below range (0-100) */
  low: number;
  /** Percentage of time in range (0-100) */
  inRange: number;
  /** Percentage of time above range (0-100) */
  high: number;
}

export interface TimeInRangeBarProps {
  /** Range percentages (must sum to ~100) */
  data: RangeData;
  /** Time period label (default: 24h) */
  period?: TimePeriod;
  /** Whether to show the legend below the bar (default: true) */
  showLegend?: boolean;
  /** Whether to show percentage labels on segments (default: true) */
  showLabels?: boolean;
  /** Whether to animate the bar on mount (default: true) */
  animated?: boolean;
  /** Target range description (default: "70-180 mg/dL") */
  targetRange?: string;
  /** Whether data is loading */
  isLoading?: boolean;
  /** Additional CSS classes */
  className?: string;
}

/** Period display labels */
export const PERIOD_LABELS: Record<TimePeriod, string> = {
  "24h": "24 Hours",
  "7d": "7 Days",
  "14d": "14 Days",
  "30d": "30 Days",
  "90d": "90 Days",
};

/** Color classes for each range */
const RANGE_COLORS = {
  low: {
    bg: "bg-red-500",
    text: "text-red-400",
  },
  inRange: {
    bg: "bg-green-500",
    text: "text-green-400",
  },
  high: {
    bg: "bg-amber-500",
    text: "text-amber-400",
  },
} as const;

/**
 * Normalize percentages to ensure they sum to 100.
 * Handles floating point precision issues.
 */
export function normalizePercentages(data: RangeData): RangeData {
  const total = data.low + data.inRange + data.high;

  if (total === 0) {
    return { low: 0, inRange: 100, high: 0 };
  }

  if (Math.abs(total - 100) < 0.01) {
    return data;
  }

  // Normalize to 100%
  const factor = 100 / total;
  const low = Math.round(data.low * factor * 10) / 10;
  const high = Math.round(data.high * factor * 10) / 10;
  // inRange absorbs any rounding differences to ensure sum is exactly 100
  // Math.max(0, ...) prevents negative values from extreme rounding edge cases
  const inRange = Math.max(0, Math.round((100 - low - high) * 10) / 10);

  return { low, inRange, high };
}

/**
 * Validate and sanitize range data.
 * Returns safe values for invalid inputs.
 */
export function sanitizeRangeData(data: RangeData): RangeData {
  const sanitize = (value: number): number => {
    if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
      return 0;
    }
    return Math.min(value, 100);
  };

  return {
    low: sanitize(data.low),
    inRange: sanitize(data.inRange),
    high: sanitize(data.high),
  };
}

/**
 * Format percentage for display.
 */
export function formatPercentage(value: number): string {
  if (value === 0) return "0%";
  if (value > 0 && value < 0.5) return "<1%";
  if (value >= 99.5 && value < 100) return ">99%";
  return `${Math.round(value)}%`;
}

/**
 * Get quality assessment based on time in range.
 */
export function getQualityAssessment(inRangePercent: number): {
  label: string;
  colorClass: string;
} {
  if (inRangePercent >= 70) {
    return { label: "Excellent", colorClass: "text-green-400" };
  }
  if (inRangePercent >= 50) {
    return { label: "Good", colorClass: "text-amber-400" };
  }
  return { label: "Needs Improvement", colorClass: "text-red-400" };
}

/** Props for individual bar segments */
interface BarSegmentProps {
  percentage: number;
  color: string;
  label: string;
  position: "start" | "middle" | "end";
  showLabels: boolean;
}

/** Individual segment of the time-in-range bar */
function BarSegment({
  percentage,
  color,
  label,
  position,
  showLabels,
}: BarSegmentProps) {
  if (percentage === 0) return null;

  const roundedClasses = clsx({
    "rounded-l-full": position === "start",
    "rounded-r-full": position === "end",
  });

  return (
    <div
      className={clsx(
        color,
        roundedClasses,
        "h-full flex items-center justify-center transition-all duration-300"
      )}
      style={{ width: `${percentage}%` }}
      title={`${label}: ${formatPercentage(percentage)}`}
    >
      {showLabels && percentage >= 10 && (
        <span className="text-xs font-medium text-white/90 drop-shadow-sm">
          {formatPercentage(percentage)}
        </span>
      )}
    </div>
  );
}

export function TimeInRangeBar({
  data,
  period = "24h",
  showLegend = true,
  showLabels = true,
  animated = true,
  targetRange = "70-180 mg/dL",
  isLoading = false,
  className,
}: TimeInRangeBarProps) {
  const prefersReducedMotion = useReducedMotion();

  // Loading skeleton
  if (isLoading) {
    return (
      <div
        className={clsx(
          "bg-slate-900 rounded-xl p-6 border border-slate-800 animate-pulse",
          className
        )}
        data-testid="time-in-range-bar"
        role="region"
        aria-label="Loading time in range data"
        aria-busy="true"
      >
        <div className="flex items-center justify-between mb-4">
          <div className="h-6 w-32 bg-slate-700 rounded" />
          <div className="h-6 w-20 bg-slate-700 rounded" />
        </div>
        <div className="h-8 bg-slate-700 rounded-full" />
        <div className="flex justify-between mt-3">
          <div className="h-4 w-16 bg-slate-700 rounded" />
          <div className="h-4 w-20 bg-slate-700 rounded" />
          <div className="h-4 w-16 bg-slate-700 rounded" />
        </div>
      </div>
    );
  }

  // Sanitize and normalize data
  const safeData = sanitizeRangeData(data);
  const normalizedData = normalizePercentages(safeData);

  const periodLabel = PERIOD_LABELS[period];
  const quality = getQualityAssessment(normalizedData.inRange);

  // Build aria description (single line to avoid awkward screen reader pauses)
  const ariaDescription = `Time in range for ${periodLabel}: ${formatPercentage(normalizedData.low)} below range, ${formatPercentage(normalizedData.inRange)} in range, ${formatPercentage(normalizedData.high)} above range. Target range: ${targetRange}.`;

  // Animation variants
  const barVariants = {
    hidden: { scaleX: 0 },
    visible: { scaleX: 1, transition: { duration: 0.6, ease: "easeOut" } },
  };

  // Determine positions for rounded corners
  const getPosition = (
    range: "low" | "inRange" | "high"
  ): "start" | "middle" | "end" => {
    const hasLow = normalizedData.low > 0;
    const hasHigh = normalizedData.high > 0;

    if (range === "low") return "start";
    if (range === "high") return "end";
    // inRange
    if (!hasLow && !hasHigh) return "start"; // Only inRange
    if (!hasLow) return "start";
    if (!hasHigh) return "end";
    return "middle";
  };

  const barContent = (
    <div
      className="h-8 rounded-full overflow-hidden flex bg-slate-700"
      role="img"
      aria-label={ariaDescription}
    >
      <BarSegment
        percentage={normalizedData.low}
        color={RANGE_COLORS.low.bg}
        label="Below range"
        position={getPosition("low")}
        showLabels={showLabels}
      />
      <BarSegment
        percentage={normalizedData.inRange}
        color={RANGE_COLORS.inRange.bg}
        label="In range"
        position={getPosition("inRange")}
        showLabels={showLabels}
      />
      <BarSegment
        percentage={normalizedData.high}
        color={RANGE_COLORS.high.bg}
        label="Above range"
        position={getPosition("high")}
        showLabels={showLabels}
      />
    </div>
  );

  return (
    <div
      className={clsx("bg-slate-900 rounded-xl p-6 border border-slate-800", className)}
      data-testid="time-in-range-bar"
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold">Time in Range</h2>
        <div className="flex items-center gap-3">
          <span className={clsx("text-sm font-medium", quality.colorClass)}>
            {quality.label}
          </span>
          <span
            className="text-sm text-slate-400 bg-slate-800 px-2 py-1 rounded"
            data-testid="period-label"
          >
            {periodLabel}
          </span>
        </div>
      </div>

      {/* Bar */}
      {animated && !prefersReducedMotion ? (
        <motion.div
          initial="hidden"
          animate="visible"
          variants={barVariants}
          style={{ originX: 0 }}
        >
          {barContent}
        </motion.div>
      ) : (
        barContent
      )}

      {/* Legend */}
      {showLegend && (
        <div
          className="flex justify-between mt-3 text-sm"
          data-testid="range-legend"
        >
          <div className="flex items-center gap-2">
            <div className={clsx("w-3 h-3 rounded-full", RANGE_COLORS.low.bg)} aria-hidden="true" />
            <span className={RANGE_COLORS.low.text}>
              Low: {formatPercentage(normalizedData.low)}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <div className={clsx("w-3 h-3 rounded-full", RANGE_COLORS.inRange.bg)} aria-hidden="true" />
            <span className={RANGE_COLORS.inRange.text}>
              In Range: {formatPercentage(normalizedData.inRange)}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <div className={clsx("w-3 h-3 rounded-full", RANGE_COLORS.high.bg)} aria-hidden="true" />
            <span className={RANGE_COLORS.high.text}>
              High: {formatPercentage(normalizedData.high)}
            </span>
          </div>
        </div>
      )}

      {/* Target range info */}
      <p className="text-slate-500 text-xs mt-3 text-center">
        Target: {targetRange}
      </p>
    </div>
  );
}

export default TimeInRangeBar;
