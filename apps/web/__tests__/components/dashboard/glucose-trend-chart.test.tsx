/**
 * Tests for the GlucoseTrendChart component.
 *
 * Glucose trend chart with colored dots, target range band,
 * and time period selector (3H, 6H, 12H, 24H).
 */

import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import {
  GlucoseTrendChart,
  type GlucoseTrendChartProps,
} from "../../../src/components/dashboard/glucose-trend-chart";

// --- Mocks ---

// Mock the useGlucoseHistory hook
const mockSetPeriod = jest.fn();
const mockRefetch = jest.fn();
let mockHookReturn = {
  readings: [] as Array<{
    value: number;
    reading_timestamp: string;
    trend: string;
    trend_rate: number | null;
    received_at: string;
    source: string;
  }>,
  isLoading: false,
  error: null as string | null,
  period: "3h" as const,
  setPeriod: mockSetPeriod,
  refetch: mockRefetch,
};

jest.mock("../../../src/hooks/use-glucose-history", () => ({
  useGlucoseHistory: () => mockHookReturn,
}));

// Mock recharts - render minimal DOM so we can test surrounding logic
jest.mock("recharts", () => ({
  ResponsiveContainer: ({
    children,
  }: {
    children: React.ReactNode;
  }) => <div data-testid="responsive-container">{children}</div>,
  ScatterChart: ({
    children,
  }: {
    children: React.ReactNode;
  }) => <div data-testid="scatter-chart">{children}</div>,
  Scatter: ({ data }: { data: unknown[] }) => (
    <div data-testid="scatter" data-count={data?.length ?? 0} />
  ),
  XAxis: () => <div data-testid="x-axis" />,
  YAxis: () => <div data-testid="y-axis" />,
  CartesianGrid: () => <div data-testid="cartesian-grid" />,
  Tooltip: () => <div data-testid="tooltip" />,
  ReferenceArea: ({ y1, y2 }: { y1: number; y2: number }) => (
    <div data-testid="reference-area" data-y1={y1} data-y2={y2} />
  ),
  Cell: () => <div data-testid="cell" />,
}));

// --- Helpers ---

function makeReading(
  value: number,
  minutesAgo: number
): (typeof mockHookReturn.readings)[0] {
  const ts = new Date(Date.now() - minutesAgo * 60_000).toISOString();
  return {
    value,
    reading_timestamp: ts,
    trend: "flat",
    trend_rate: null,
    received_at: ts,
    source: "dexcom",
  };
}

function renderChart(props: Partial<GlucoseTrendChartProps> = {}) {
  return render(<GlucoseTrendChart {...props} />);
}

// --- Tests ---

beforeEach(() => {
  jest.clearAllMocks();
  mockHookReturn = {
    readings: [],
    isLoading: false,
    error: null,
    period: "3h",
    setPeriod: mockSetPeriod,
    refetch: mockRefetch,
  };
});

describe("GlucoseTrendChart", () => {
  describe("loading state", () => {
    it("renders loading skeleton when loading with no data", () => {
      mockHookReturn.isLoading = true;
      renderChart();
      expect(
        screen.getByRole("region", {
          name: /loading glucose trend chart/i,
        })
      ).toBeInTheDocument();
      expect(screen.getByRole("region")).toHaveAttribute(
        "aria-busy",
        "true"
      );
    });

    it("has correct test id", () => {
      mockHookReturn.isLoading = true;
      renderChart();
      expect(screen.getByTestId("glucose-trend-chart")).toBeInTheDocument();
    });
  });

  describe("error state", () => {
    it("shows error message when there is an error and no data", () => {
      mockHookReturn.error = "Network error";
      renderChart();
      expect(
        screen.getByText("Unable to load glucose history")
      ).toBeInTheDocument();
    });

    it("still shows period selector in error state", () => {
      mockHookReturn.error = "Network error";
      renderChart();
      expect(
        screen.getByRole("radiogroup", { name: /time period/i })
      ).toBeInTheDocument();
    });
  });

  describe("empty state", () => {
    it("shows empty message when no readings", () => {
      renderChart();
      expect(
        screen.getByText("No glucose readings yet")
      ).toBeInTheDocument();
    });

    it("still shows period selector in empty state", () => {
      renderChart();
      expect(
        screen.getByRole("radiogroup", { name: /time period/i })
      ).toBeInTheDocument();
    });
  });

  describe("data rendering", () => {
    it("renders the chart when readings are available", () => {
      mockHookReturn.readings = [
        makeReading(120, 30),
        makeReading(150, 20),
        makeReading(100, 10),
      ];
      renderChart();
      expect(screen.getByTestId("scatter-chart")).toBeInTheDocument();
      expect(screen.getByTestId("reference-area")).toBeInTheDocument();
    });

    it("shows the chart heading", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      expect(screen.getByText("Glucose Trend")).toBeInTheDocument();
    });

    it("sets aria-label with period", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      expect(
        screen.getByRole("region", {
          name: /glucose trend chart, 3h view/i,
        })
      ).toBeInTheDocument();
    });

    it("renders the range legend", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      expect(screen.getByText("70-180 Target")).toBeInTheDocument();
      expect(screen.getByText("High/Low")).toBeInTheDocument();
      expect(screen.getByText("Urgent")).toBeInTheDocument();
    });

    it("renders target range band at 70-180", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      const band = screen.getByTestId("reference-area");
      expect(band).toHaveAttribute("data-y1", "70");
      expect(band).toHaveAttribute("data-y2", "180");
    });
  });

  describe("period selector", () => {
    it("renders all period options", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      expect(screen.getByRole("radio", { name: "3H" })).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: "6H" })).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: "12H" })).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: "24H" })).toBeInTheDocument();
    });

    it("marks the active period as checked", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      expect(screen.getByRole("radio", { name: "3H" })).toHaveAttribute(
        "aria-checked",
        "true"
      );
      expect(screen.getByRole("radio", { name: "6H" })).toHaveAttribute(
        "aria-checked",
        "false"
      );
    });

    it("calls setPeriod when clicking a period button", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart();
      fireEvent.click(screen.getByRole("radio", { name: "12H" }));
      expect(mockSetPeriod).toHaveBeenCalledWith("12h");
    });
  });

  describe("refreshKey", () => {
    it("calls refetch when refreshKey changes", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      const { rerender } = render(<GlucoseTrendChart refreshKey={1} />);
      expect(mockRefetch).not.toHaveBeenCalled();

      rerender(<GlucoseTrendChart refreshKey={2} />);
      expect(mockRefetch).toHaveBeenCalledTimes(1);
    });

    it("does not refetch on initial render with refreshKey=0", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      render(<GlucoseTrendChart refreshKey={0} />);
      expect(mockRefetch).not.toHaveBeenCalled();
    });
  });

  describe("className prop", () => {
    it("applies custom className", () => {
      mockHookReturn.readings = [makeReading(120, 5)];
      renderChart({ className: "custom-class" });
      expect(screen.getByTestId("glucose-trend-chart")).toHaveClass(
        "custom-class"
      );
    });
  });
});
