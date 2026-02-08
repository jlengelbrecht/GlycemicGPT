"use client";

/**
 * GlucoseHero Component
 *
 * Story 4.2: GlucoseHero Component
 * Prominently displays current glucose with trend arrow so users
 * can understand their status in under 2 seconds.
 */

import { motion, useReducedMotion } from "framer-motion";
import clsx from "clsx";
import {
  type TrendDirection,
  TREND_ARROWS,
  TREND_DESCRIPTIONS,
} from "./trend-arrow";

// Glucose range classification
export type GlucoseRange =
  | "urgentLow"
  | "low"
  | "inRange"
  | "high"
  | "urgentHigh";

/** Glucose range thresholds in mg/dL */
export const GLUCOSE_THRESHOLDS = {
  URGENT_LOW: 55,
  LOW: 70,
  HIGH: 180,
  URGENT_HIGH: 250,
} as const;

export interface GlucoseHeroProps {
  /** Current glucose value in mg/dL */
  value: number | null;
  /** Trend direction */
  trend: TrendDirection;
  /** Insulin on Board in units */
  iob: number | null;
  /** Carbs on Board in grams */
  cob: number | null;
  /** Unit label (default: mg/dL) */
  unit?: string;
  /** Minutes since last reading */
  minutesAgo?: number;
  /** Whether data is considered stale (>10 minutes) */
  isStale?: boolean;
  /** Whether data is currently loading */
  isLoading?: boolean;
}


/**
 * Classify glucose value into range category.
 */
export function classifyGlucose(value: number | null): GlucoseRange {
  if (value === null) return "inRange";
  if (value < GLUCOSE_THRESHOLDS.URGENT_LOW) return "urgentLow";
  if (value < GLUCOSE_THRESHOLDS.LOW) return "low";
  if (value <= GLUCOSE_THRESHOLDS.HIGH) return "inRange";
  if (value <= GLUCOSE_THRESHOLDS.URGENT_HIGH) return "high";
  return "urgentHigh";
}

// Color configuration per glucose range
const rangeColors: Record<GlucoseRange, { text: string; bg: string }> = {
  urgentLow: { text: "text-red-500", bg: "bg-red-500/10" },
  low: { text: "text-amber-400", bg: "bg-amber-500/10" },
  inRange: { text: "text-green-400", bg: "bg-green-500/10" },
  high: { text: "text-amber-400", bg: "bg-amber-500/10" },
  urgentHigh: { text: "text-red-500", bg: "bg-red-500/10" },
};

/**
 * Determine if pulse animation should be shown.
 */
export function shouldPulse(range: GlucoseRange): "strong" | "subtle" | null {
  if (range === "urgentLow" || range === "urgentHigh") return "strong";
  if (range === "low" || range === "high") return "subtle";
  return null;
}

// Animation variants for pulse effects
const pulseVariants = {
  subtle: {
    scale: [1, 1.02, 1],
    opacity: [1, 0.9, 1],
    transition: {
      duration: 2,
      repeat: Infinity,
      ease: "easeInOut",
    },
  },
  strong: {
    scale: [1, 1.05, 1],
    opacity: [1, 0.8, 1],
    transition: {
      duration: 1,
      repeat: Infinity,
      ease: "easeInOut",
    },
  },
};

/**
 * Validate and sanitize numeric value.
 * Returns null for invalid values (NaN, Infinity, negative).
 */
function sanitizeValue(value: number | null, allowNegative = false): number | null {
  if (value === null) return null;
  if (typeof value !== "number") return null;
  if (!Number.isFinite(value)) return null;
  if (!allowNegative && value < 0) return null;
  return value;
}

export function GlucoseHero({
  value,
  trend,
  iob,
  cob,
  unit = "mg/dL",
  minutesAgo,
  isStale = false,
  isLoading = false,
}: GlucoseHeroProps) {
  // Use Framer Motion's hook for SSR-safe reduced motion detection
  const prefersReducedMotion = useReducedMotion();

  // Loading skeleton state
  if (isLoading) {
    return (
      <div
        className="rounded-xl p-8 border border-slate-800 bg-slate-900 animate-pulse"
        role="region"
        aria-label="Loading glucose reading"
        aria-busy="true"
      >
        <div className="flex flex-col items-center">
          <div className="h-16 w-32 bg-slate-700 rounded mb-4" />
          <div className="h-6 w-16 bg-slate-700 rounded mb-4" />
          <div className="flex gap-6">
            <div className="h-10 w-12 bg-slate-700 rounded" />
            <div className="h-10 w-12 bg-slate-700 rounded" />
          </div>
        </div>
      </div>
    );
  }

  // Defensive: sanitize numeric values
  const safeValue = sanitizeValue(value);
  const safeIob = sanitizeValue(iob, true); // IoB can be negative (rare but possible)
  const safeCob = sanitizeValue(cob);

  const range = classifyGlucose(safeValue);
  const colors = rangeColors[range];
  const pulseType = shouldPulse(range);
  const arrow = TREND_ARROWS[trend];
  const trendDescription = TREND_DESCRIPTIONS[trend];

  // Format display value
  const displayValue = safeValue !== null ? Math.round(safeValue).toString() : "--";

  // Build accessible description
  const ariaLabel = safeValue !== null
    ? `Glucose ${displayValue} ${unit}, ${trendDescription}`
    : "Glucose data unavailable";

  return (
    <div
      className={clsx(
        "rounded-xl p-8 border border-slate-800",
        colors.bg
      )}
      role="region"
      aria-label="Current glucose reading"
    >
      <motion.div
        className="flex flex-col items-center justify-center text-center"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        {/* Main glucose display */}
        <motion.div
          className="flex items-center gap-4 mb-4"
          animate={
            pulseType && !prefersReducedMotion
              ? pulseVariants[pulseType]
              : undefined
          }
          aria-live="polite"
          aria-atomic="true"
        >
          <span
            className={clsx(
              "text-7xl font-bold tabular-nums",
              colors.text
            )}
            data-testid="glucose-value"
            aria-label={ariaLabel}
          >
            {displayValue}
          </span>
          <span
            className={clsx(
              "text-5xl",
              // Trend arrow inherits glucose range color for visual consistency
              safeValue !== null ? colors.text : "text-slate-400"
            )}
            data-testid="trend-arrow"
            aria-hidden="true"
          >
            {arrow}
          </span>
        </motion.div>

        {/* Unit label */}
        <p
          className="text-slate-400 text-lg"
          data-testid="glucose-unit"
        >
          {unit}
        </p>

        {/* Stale data warning */}
        {isStale && (
          <p
            className="text-amber-400 text-sm mt-2 flex items-center gap-1"
            data-testid="stale-warning"
            role="alert"
          >
            <span aria-hidden="true">⏱️</span>
            <span>Data is {minutesAgo ?? "10"}+ minutes old</span>
          </p>
        )}

        {/* Secondary metrics: IoB and CoB */}
        <div
          className="flex items-center gap-6 mt-4 text-sm"
          data-testid="secondary-metrics"
        >
          <div className="flex flex-col items-center">
            <span className="text-slate-500 text-xs uppercase tracking-wide">
              IoB
            </span>
            <span
              className="text-slate-300 font-medium"
              data-testid="iob-value"
            >
              {safeIob !== null ? `${safeIob.toFixed(1)}u` : "--"}
            </span>
          </div>
          <div className="w-px h-6 bg-slate-700" aria-hidden="true" />
          <div className="flex flex-col items-center">
            <span className="text-slate-500 text-xs uppercase tracking-wide">
              CoB
            </span>
            <span
              className="text-slate-300 font-medium"
              data-testid="cob-value"
            >
              {safeCob !== null ? `${Math.round(safeCob)}g` : "--"}
            </span>
          </div>
        </div>
      </motion.div>
    </div>
  );
}

// Re-export TrendDirection for backwards compatibility
// Primary source is now trend-arrow.tsx
export { type TrendDirection } from "./trend-arrow";

export default GlucoseHero;
