"use client";

/**
 * GlucoseHero Component
 *
 * Story 4.2: GlucoseHero Component
 * Story 4.6: Dashboard Accessibility
 * Prominently displays current glucose with trend arrow so users
 * can understand their status in under 2 seconds.
 *
 * Accessibility features:
 * - Screen reader announcements with value, trend, and range status
 * - Dynamic aria-live (assertive for urgent, polite for normal)
 * - Keyboard focusable with visible focus ring
 * - Accessible labels for IoB/CoB metrics
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

// Accessible range status descriptions
type RangeStatus = "in-range" | "low" | "high" | "urgent-low" | "urgent-high";

const RANGE_STATUS_TEXT: Record<RangeStatus, string> = {
  "in-range": "in target range",
  "low": "below target",
  "high": "above target",
  "urgent-low": "dangerously low",
  "urgent-high": "dangerously high",
};

/**
 * Get accessible range status text for screen readers.
 */
export function getRangeStatus(range: GlucoseRange): RangeStatus {
  const mapping: Record<GlucoseRange, RangeStatus> = {
    inRange: "in-range",
    low: "low",
    high: "high",
    urgentLow: "urgent-low",
    urgentHigh: "urgent-high",
  };
  return mapping[range];
}

/**
 * Build accessible announcement for screen readers.
 * Format: "Glucose 142 milligrams per deciliter, falling slowly, in target range"
 */
export function buildGlucoseAnnouncement(
  value: number | null,
  trendDescription: string,
  rangeStatus: RangeStatus
): string {
  if (value === null) {
    return "Glucose reading unavailable";
  }

  const rangeText = RANGE_STATUS_TEXT[rangeStatus];
  return `Glucose ${Math.round(value)} milligrams per deciliter, ${trendDescription}, ${rangeText}`;
}

/**
 * Determine if glucose state is urgent (requires assertive announcement).
 */
export function isUrgentState(range: GlucoseRange): boolean {
  return range === "urgentLow" || range === "urgentHigh";
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

  // Accessibility: Build announcement and determine aria-live priority
  const rangeStatus = getRangeStatus(range);
  const announcement = buildGlucoseAnnouncement(safeValue, trendDescription, rangeStatus);
  const isUrgent = isUrgentState(range);
  const ariaLivePriority = isUrgent ? "assertive" : "polite";

  return (
    <div
      className={clsx(
        "rounded-xl p-8 border border-slate-800",
        "focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900",
        colors.bg
      )}
      role="region"
      aria-label="Current glucose reading"
      tabIndex={0}
    >
      <motion.div
        className="flex flex-col items-center justify-center text-center"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        {/* Main glucose display */}
        {/* Main glucose display with dynamic aria-live priority */}
        <motion.div
          className="flex items-center gap-4 mb-4"
          animate={
            pulseType && !prefersReducedMotion
              ? pulseVariants[pulseType]
              : undefined
          }
          aria-live={ariaLivePriority}
          aria-atomic="true"
        >
          <span
            className={clsx(
              "text-7xl font-bold tabular-nums",
              colors.text
            )}
            data-testid="glucose-value"
            aria-label={announcement}
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

        {/* Secondary metrics: IoB and CoB with accessible labels */}
        <div
          className="flex items-center gap-6 mt-4 text-sm"
          role="group"
          aria-label="Insulin and carbohydrate metrics"
          data-testid="secondary-metrics"
        >
          <div
            className="flex flex-col items-center"
            aria-label={safeIob !== null ? `Insulin on board: ${safeIob.toFixed(1)} units` : "Insulin on board: unavailable"}
          >
            <span className="text-slate-500 text-xs uppercase tracking-wide" aria-hidden="true">
              IoB
            </span>
            <span className="sr-only">Insulin on board</span>
            <span
              className="text-slate-300 font-medium"
              data-testid="iob-value"
              aria-hidden="true"
            >
              {safeIob !== null ? `${safeIob.toFixed(1)}u` : "--"}
            </span>
          </div>
          <div className="w-px h-6 bg-slate-700" aria-hidden="true" />
          <div
            className="flex flex-col items-center"
            aria-label={safeCob !== null ? `Carbohydrates on board: ${Math.round(safeCob)} grams` : "Carbohydrates on board: unavailable"}
          >
            <span className="text-slate-500 text-xs uppercase tracking-wide" aria-hidden="true">
              CoB
            </span>
            <span className="sr-only">Carbohydrates on board</span>
            <span
              className="text-slate-300 font-medium"
              data-testid="cob-value"
              aria-hidden="true"
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
