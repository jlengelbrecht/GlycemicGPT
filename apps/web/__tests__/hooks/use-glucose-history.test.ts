/**
 * useGlucoseHistory Hook Tests
 *
 * Tests for the glucose history data fetching hook
 * used by the GlucoseTrendChart component.
 */

import { renderHook, act, waitFor } from "@testing-library/react";
import {
  useGlucoseHistory,
  type ChartTimePeriod,
} from "@/hooks/use-glucose-history";
import { getGlucoseHistory } from "@/lib/api";

// Mock the API module
jest.mock("@/lib/api", () => ({
  getGlucoseHistory: jest.fn(),
}));

const mockGetGlucoseHistory = getGlucoseHistory as jest.MockedFunction<
  typeof getGlucoseHistory
>;

function makeReadings(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    value: 100 + i * 5,
    reading_timestamp: new Date(
      Date.now() - (count - i) * 5 * 60_000
    ).toISOString(),
    trend: "flat",
    trend_rate: null,
    received_at: new Date().toISOString(),
    source: "dexcom",
  }));
}

beforeEach(() => {
  jest.clearAllMocks();
  mockGetGlucoseHistory.mockResolvedValue({
    readings: makeReadings(5),
    count: 5,
  });
});

describe("useGlucoseHistory", () => {
  it("fetches data on mount with default 3h period", async () => {
    const { result } = renderHook(() => useGlucoseHistory("3h"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetGlucoseHistory).toHaveBeenCalledWith(180, 288);
    expect(result.current.readings).toHaveLength(5);
    expect(result.current.error).toBeNull();
    expect(result.current.period).toBe("3h");
  });

  it("uses correct minutes for each period", async () => {
    const periods: [ChartTimePeriod, number][] = [
      ["3h", 180],
      ["6h", 360],
      ["12h", 720],
      ["24h", 1440],
    ];

    for (const [period, expectedMinutes] of periods) {
      mockGetGlucoseHistory.mockClear();
      mockGetGlucoseHistory.mockResolvedValue({
        readings: makeReadings(3),
        count: 3,
      });

      const { result } = renderHook(() => useGlucoseHistory(period));

      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });

      expect(mockGetGlucoseHistory).toHaveBeenCalledWith(
        expectedMinutes,
        288
      );
    }
  });

  it("refetches when period changes", async () => {
    const { result } = renderHook(() => useGlucoseHistory("3h"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetGlucoseHistory).toHaveBeenCalledTimes(1);

    act(() => {
      result.current.setPeriod("6h");
    });

    await waitFor(() => {
      expect(mockGetGlucoseHistory).toHaveBeenCalledTimes(2);
    });

    expect(mockGetGlucoseHistory).toHaveBeenLastCalledWith(360, 288);
  });

  it("handles API errors gracefully", async () => {
    mockGetGlucoseHistory.mockRejectedValueOnce(
      new Error("Network failure")
    );

    const { result } = renderHook(() => useGlucoseHistory("3h"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBe("Network failure");
    expect(result.current.readings).toHaveLength(0);
  });

  it("handles non-Error thrown values", async () => {
    mockGetGlucoseHistory.mockRejectedValueOnce("string error");

    const { result } = renderHook(() => useGlucoseHistory("3h"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBe("Failed to load history");
  });

  it("exposes a refetch function", async () => {
    const { result } = renderHook(() => useGlucoseHistory("3h"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetGlucoseHistory).toHaveBeenCalledTimes(1);

    act(() => {
      result.current.refetch();
    });

    await waitFor(() => {
      expect(mockGetGlucoseHistory).toHaveBeenCalledTimes(2);
    });
  });

  it("starts in loading state", () => {
    const { result } = renderHook(() => useGlucoseHistory("3h"));
    expect(result.current.isLoading).toBe(true);
  });
});
