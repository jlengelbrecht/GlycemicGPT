"use client";

import { useState, useEffect, useCallback } from "react";
import { getTargetGlucoseRange } from "@/lib/api";

export interface GlucoseThresholds {
  urgentLow: number;
  low: number;
  high: number;
  urgentHigh: number;
}

const DEFAULT_THRESHOLDS: GlucoseThresholds = {
  urgentLow: 55,
  low: 70,
  high: 180,
  urgentHigh: 250,
};

/**
 * Fetches the user's glucose range thresholds from the backend.
 * Returns defaults until the fetch completes or on error.
 */
export function useGlucoseRange(): GlucoseThresholds {
  const [thresholds, setThresholds] =
    useState<GlucoseThresholds>(DEFAULT_THRESHOLDS);

  const fetchRange = useCallback(async () => {
    try {
      const data = await getTargetGlucoseRange();
      setThresholds({
        urgentLow: data.urgent_low,
        low: data.low_target,
        high: data.high_target,
        urgentHigh: data.urgent_high,
      });
    } catch {
      // Keep defaults on error
    }
  }, []);

  useEffect(() => {
    fetchRange();
  }, [fetchRange]);

  return thresholds;
}
