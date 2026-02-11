"use client";

/**
 * useGlucoseHistory Hook
 *
 * Fetches historical glucose readings for the trend chart.
 * Manages time period selection and data refreshing.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import {
  getGlucoseHistory,
  type GlucoseHistoryReading,
} from "@/lib/api";

export type ChartTimePeriod = "3h" | "6h" | "12h" | "24h";

const PERIOD_TO_MINUTES: Record<ChartTimePeriod, number> = {
  "3h": 180,
  "6h": 360,
  "12h": 720,
  "24h": 1440,
};

export interface UseGlucoseHistoryReturn {
  readings: GlucoseHistoryReading[];
  isLoading: boolean;
  error: string | null;
  period: ChartTimePeriod;
  setPeriod: (p: ChartTimePeriod) => void;
  refetch: () => void;
}

export function useGlucoseHistory(
  initialPeriod: ChartTimePeriod = "3h"
): UseGlucoseHistoryReturn {
  const [readings, setReadings] = useState<GlucoseHistoryReading[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<ChartTimePeriod>(initialPeriod);
  const isMountedRef = useRef(true);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const minutes = PERIOD_TO_MINUTES[period];
      const data = await getGlucoseHistory(minutes, 288);
      if (isMountedRef.current) {
        setReadings(data.readings);
      }
    } catch (err) {
      if (isMountedRef.current) {
        setError(
          err instanceof Error ? err.message : "Failed to load history"
        );
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false);
      }
    }
  }, [period]);

  useEffect(() => {
    isMountedRef.current = true;
    fetchData();
    return () => {
      isMountedRef.current = false;
    };
  }, [fetchData]);

  return { readings, isLoading, error, period, setPeriod, refetch: fetchData };
}
