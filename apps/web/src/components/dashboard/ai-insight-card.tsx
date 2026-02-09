"use client";

/**
 * AIInsightCard Component
 *
 * Story 5.7: AI Insight Card
 * Displays an AI-generated insight (daily brief, meal analysis,
 * or correction analysis) with acknowledge/dismiss actions.
 *
 * Accessibility features:
 * - Semantic article element with aria-label
 * - Keyboard-navigable action buttons
 * - Screen reader status announcements via aria-live
 * - Visible focus rings
 * - Respects reduced motion preferences
 */

import { useState } from "react";
import { motion, useReducedMotion } from "framer-motion";
import clsx from "clsx";
import {
  FileText,
  Utensils,
  Syringe,
  Check,
  X,
  ChevronDown,
  ChevronUp,
} from "lucide-react";

// Analysis type configuration
const ANALYSIS_CONFIG = {
  daily_brief: {
    icon: FileText,
    color: "text-blue-400",
    bg: "bg-blue-500/10",
    border: "border-blue-500/20",
    label: "Daily Brief",
  },
  meal_analysis: {
    icon: Utensils,
    color: "text-amber-400",
    bg: "bg-amber-500/10",
    border: "border-amber-500/20",
    label: "Meal Analysis",
  },
  correction_analysis: {
    icon: Syringe,
    color: "text-purple-400",
    bg: "bg-purple-500/10",
    border: "border-purple-500/20",
    label: "Correction Analysis",
  },
} as const;

type AnalysisType = keyof typeof ANALYSIS_CONFIG;

export interface InsightData {
  id: string;
  analysis_type: AnalysisType;
  title: string;
  content: string;
  created_at: string;
  status: "pending" | "acknowledged" | "dismissed";
}

export interface AIInsightCardProps {
  insight: InsightData;
  onRespond?: (
    analysisType: AnalysisType,
    analysisId: string,
    response: "acknowledged" | "dismissed",
    reason?: string
  ) => Promise<void>;
}

/**
 * Format a date string for display.
 */
function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return "Unknown date";
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/**
 * Status badge component.
 */
function StatusBadge({ status }: { status: InsightData["status"] }) {
  const config = {
    pending: {
      text: "New",
      className: "bg-blue-500/20 text-blue-300",
    },
    acknowledged: {
      text: "Acknowledged",
      className: "bg-green-500/20 text-green-300",
    },
    dismissed: {
      text: "Dismissed",
      className: "bg-slate-500/20 text-slate-400",
    },
  };

  const { text, className } = config[status];

  return (
    <span
      className={clsx(
        "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium",
        className
      )}
    >
      {text}
    </span>
  );
}

export function AIInsightCard({ insight, onRespond }: AIInsightCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);
  const [isResponding, setIsResponding] = useState(false);
  const [localStatus, setLocalStatus] = useState(insight.status);
  const [error, setError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState<string | null>(null);
  const prefersReducedMotion = useReducedMotion();

  const config = ANALYSIS_CONFIG[insight.analysis_type];
  const Icon = config.icon;

  // Truncate content for collapsed view
  const maxPreviewLength = 200;
  const needsTruncation = insight.content.length > maxPreviewLength;
  const previewContent = needsTruncation
    ? insight.content.slice(0, maxPreviewLength) + "..."
    : insight.content;

  const handleRespond = async (response: "acknowledged" | "dismissed") => {
    if (!onRespond || isResponding) return;

    setIsResponding(true);
    setError(null);
    try {
      await onRespond(
        insight.analysis_type,
        insight.id,
        response
      );
      setLocalStatus(response);
      setAnnouncement(
        `Insight ${response === "acknowledged" ? "acknowledged" : "dismissed"}`
      );
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to record response"
      );
    } finally {
      setIsResponding(false);
    }
  };

  return (
    <motion.article
      className={clsx(
        "rounded-xl border p-5 transition-colors",
        "bg-slate-900",
        config.border,
        "focus-within:ring-2 focus-within:ring-blue-500 focus-within:ring-offset-2 focus-within:ring-offset-slate-950"
      )}
      initial={prefersReducedMotion ? false : { opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      aria-label={`${config.label}: ${insight.title}`}
    >
      {/* Screen reader announcement for status changes */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">
        {announcement}
      </div>

      {/* Header row */}
      <div className="flex items-start gap-3 mb-3">
        <div
          className={clsx(
            "p-2 rounded-lg shrink-0",
            config.bg
          )}
          aria-hidden="true"
        >
          <Icon className={clsx("h-5 w-5", config.color)} />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span
              className={clsx("text-xs font-medium uppercase tracking-wider", config.color)}
            >
              {config.label}
            </span>
            <StatusBadge status={localStatus} />
          </div>
          <h3 className="text-sm font-semibold text-slate-200 leading-tight">
            {insight.title}
          </h3>
          <time
            className="text-xs text-slate-500 mt-0.5 block"
            dateTime={insight.created_at}
          >
            {formatDate(insight.created_at)}
          </time>
        </div>
      </div>

      {/* Content */}
      <div className="text-sm text-slate-300 leading-relaxed whitespace-pre-line">
        {isExpanded ? insight.content : previewContent}
      </div>

      {/* Expand/collapse toggle */}
      {needsTruncation && (
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className={clsx(
            "mt-2 text-xs font-medium flex items-center gap-1",
            "text-slate-400 hover:text-slate-200 transition-colors",
            "focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
          )}
          aria-expanded={isExpanded}
          aria-label={isExpanded ? "Show less" : "Show more"}
        >
          {isExpanded ? (
            <>
              Show less <ChevronUp className="h-3 w-3" />
            </>
          ) : (
            <>
              Show more <ChevronDown className="h-3 w-3" />
            </>
          )}
        </button>
      )}

      {/* Error message */}
      {error && (
        <p className="mt-2 text-xs text-red-400" role="alert">
          {error}
        </p>
      )}

      {/* Action buttons (only show for pending insights) */}
      {localStatus === "pending" && onRespond && (
        <div
          className="flex items-center gap-2 mt-4 pt-3 border-t border-slate-800"
          role="group"
          aria-label="Insight actions"
        >
          <button
            onClick={() => handleRespond("acknowledged")}
            disabled={isResponding}
            className={clsx(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium",
              "bg-green-500/10 text-green-400 hover:bg-green-500/20",
              "transition-colors",
              "focus:outline-none focus-visible:ring-2 focus-visible:ring-green-500",
              "disabled:opacity-50 disabled:cursor-not-allowed"
            )}
            aria-label="Acknowledge this insight"
          >
            <Check className="h-3.5 w-3.5" aria-hidden="true" />
            Acknowledge
          </button>
          <button
            onClick={() => handleRespond("dismissed")}
            disabled={isResponding}
            className={clsx(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium",
              "bg-slate-700/50 text-slate-400 hover:bg-slate-700",
              "transition-colors",
              "focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-500",
              "disabled:opacity-50 disabled:cursor-not-allowed"
            )}
            aria-label="Dismiss this insight"
          >
            <X className="h-3.5 w-3.5" aria-hidden="true" />
            Dismiss
          </button>
        </div>
      )}
    </motion.article>
  );
}
