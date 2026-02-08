/**
 * Dashboard Components
 *
 * Barrel export for dashboard-specific components.
 */

export {
  GlucoseHero,
  type GlucoseHeroProps,
  type GlucoseRange,
  classifyGlucose,
  shouldPulse,
  GLUCOSE_THRESHOLDS,
} from "./glucose-hero";

export {
  TrendArrow,
  type TrendArrowProps,
  type TrendDirection,
  type TrendArrowSize,
  TREND_ARROWS,
  TREND_DESCRIPTIONS,
  getTrendArrow,
  getTrendDescription,
  isRising,
  isFalling,
  isRapidChange,
  isStable,
  isUnknown,
} from "./trend-arrow";
