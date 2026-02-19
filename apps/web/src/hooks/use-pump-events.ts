"use client";

/**
 * usePumpEvents Hook
 *
 * Fetches pump event history (bolus, basal, etc.) for chart overlays.
 * Mirrors the useGlucoseHistory pattern with generation-counter fetch.
 */

import { useState, useEffect, useCallback, useRef } from "react";
import {
  getPumpEventHistory,
  type PumpEventReading,
} from "@/lib/api";
import type { ChartTimePeriod } from "./use-glucose-history";

const PERIOD_TO_MINUTES: Record<ChartTimePeriod, number> = {
  "3h": 180,
  "6h": 360,
  "12h": 720,
  "24h": 1440,
};

export interface UsePumpEventsReturn {
  events: PumpEventReading[];
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

export function usePumpEvents(
  period: ChartTimePeriod
): UsePumpEventsReturn {
  const [events, setEvents] = useState<PumpEventReading[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const fetchGenRef = useRef(0);

  const fetchData = useCallback(async () => {
    const gen = ++fetchGenRef.current;
    setIsLoading(true);
    setError(null);
    try {
      const minutes = PERIOD_TO_MINUTES[period];
      const data = await getPumpEventHistory(minutes);
      if (gen === fetchGenRef.current) {
        setEvents(data.events);
      }
    } catch (err) {
      if (gen === fetchGenRef.current) {
        setError(
          err instanceof Error ? err.message : "Failed to load pump events"
        );
      }
    } finally {
      if (gen === fetchGenRef.current) {
        setIsLoading(false);
      }
    }
  }, [period]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return { events, isLoading, error, refetch: fetchData };
}
