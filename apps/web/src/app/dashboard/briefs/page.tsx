"use client";

/**
 * AI Insights Page (formerly Daily Briefs)
 *
 * Story 5.7: AI Insight Card
 * Displays a unified feed of AI-generated insights including
 * daily briefs, meal analyses, and correction analyses.
 *
 * Users can acknowledge or dismiss insights to track which
 * suggestions they've reviewed.
 */

import { useState, useEffect, useCallback } from "react";
import { FileText, Loader2, RefreshCw } from "lucide-react";
import { AIInsightCard, type InsightData } from "@/components/dashboard";
import { getInsightDetail, type InsightDetail } from "@/lib/api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8000";

interface InsightsResponse {
  insights: InsightData[];
  total: number;
}

export default function BriefsPage() {
  const [insights, setInsights] = useState<InsightData[]>([]);
  const [total, setTotal] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchInsights = useCallback(async () => {
    try {
      setError(null);
      const response = await fetch(`${API_BASE_URL}/api/ai/insights?limit=20`, {
        credentials: "include",
      });

      if (response.status === 401) {
        // Not authenticated - show empty state
        setInsights([]);
        setTotal(0);
        setIsLoading(false);
        return;
      }

      if (!response.ok) {
        throw new Error(`Failed to fetch insights: ${response.status}`);
      }

      const data: InsightsResponse = await response.json();
      setInsights(data.insights);
      setTotal(data.total);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load insights");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchInsights();
  }, [fetchInsights]);

  const handleRespond = async (
    analysisType: string,
    analysisId: string,
    response: "acknowledged" | "dismissed",
    reason?: string
  ) => {
    const res = await fetch(
      `${API_BASE_URL}/api/ai/insights/${analysisType}/${analysisId}/respond`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ response, reason }),
      }
    );

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}));
      throw new Error(errorData.detail || "Failed to respond");
    }
  };

  const handleFetchDetail = async (
    analysisType: string,
    analysisId: string
  ): Promise<InsightDetail> => {
    return getInsightDetail(analysisType, analysisId);
  };

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">AI Insights</h1>
          <p className="text-slate-400">
            AI-powered analysis of your glucose patterns
          </p>
        </div>
        {!isLoading && (
          <button
            onClick={() => {
              setIsLoading(true);
              fetchInsights();
            }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-slate-800 text-slate-300 hover:bg-slate-700 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            aria-label="Refresh insights"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Refresh
          </button>
        )}
      </div>

      {/* Loading state */}
      {isLoading && (
        <div
          className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center"
          role="status"
          aria-label="Loading insights"
        >
          <Loader2 className="h-8 w-8 text-blue-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">Loading insights...</p>
        </div>
      )}

      {/* Error state */}
      {error && !isLoading && (
        <div
          className="bg-red-500/10 rounded-xl p-6 border border-red-500/20 text-center"
          role="alert"
        >
          <p className="text-red-400">{error}</p>
          <button
            onClick={() => {
              setIsLoading(true);
              fetchInsights();
            }}
            className="mt-3 text-sm text-red-300 hover:text-red-200 underline"
          >
            Try again
          </button>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !error && insights.length === 0 && (
        <div className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center">
          <div className="flex justify-center mb-4">
            <div className="p-4 bg-blue-500/10 rounded-full">
              <FileText className="h-12 w-12 text-blue-400" />
            </div>
          </div>
          <h2 className="text-xl font-semibold mb-2">No Insights Yet</h2>
          <p className="text-slate-400 max-w-md mx-auto">
            AI insights will appear here once you have enough glucose data.
            Connect your Dexcom CGM and Tandem pump to get started.
          </p>
        </div>
      )}

      {/* Insights feed */}
      {!isLoading && !error && insights.length > 0 && (
        <>
          <p className="text-sm text-slate-500">
            Showing {insights.length} of {total} insights
          </p>
          <div className="grid gap-4">
            {insights.map((insight) => (
              <AIInsightCard
                key={insight.id}
                insight={insight}
                onRespond={handleRespond}
                onFetchDetail={handleFetchDetail}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
