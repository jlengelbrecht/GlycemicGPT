/**
 * Tests for the TimeInRangeBar component.
 *
 * Story 4.4: Time in Range Bar Component
 */

import { render, screen, fireEvent } from "@testing-library/react";
import {
  TimeInRangeBar,
  normalizePercentages,
  sanitizeRangeData,
  formatPercentage,
  getQualityAssessment,
  PERIOD_LABELS,
  type RangeData,
  type TimePeriod,
} from "../../../src/components/dashboard/time-in-range-bar";
import TimeInRangeBarDefault from "../../../src/components/dashboard/time-in-range-bar";

// Mock framer-motion
const mockUseReducedMotion = jest.fn(() => false);

jest.mock("framer-motion", () => ({
  motion: {
    div: ({
      children,
      className,
      style,
      ...props
    }: {
      children: React.ReactNode;
      className?: string;
      style?: React.CSSProperties;
      [key: string]: unknown;
    }) => (
      <div className={className} style={style} {...props}>
        {children}
      </div>
    ),
  },
  useReducedMotion: () => mockUseReducedMotion(),
}));

beforeEach(() => {
  mockUseReducedMotion.mockReturnValue(false);
});

describe("PERIOD_LABELS constant", () => {
  it("contains all time periods", () => {
    expect(PERIOD_LABELS["24h"]).toBe("24 Hours");
    expect(PERIOD_LABELS["3d"]).toBe("3 Days");
    expect(PERIOD_LABELS["7d"]).toBe("7 Days");
    expect(PERIOD_LABELS["14d"]).toBe("14 Days");
    expect(PERIOD_LABELS["30d"]).toBe("30 Days");
  });
});

describe("normalizePercentages", () => {
  it("returns data unchanged if already sums to 100", () => {
    const data: RangeData = { low: 10, inRange: 70, high: 20 };
    const result = normalizePercentages(data);

    expect(result).toEqual(data);
  });

  it("normalizes data that sums to more than 100", () => {
    const data: RangeData = { low: 20, inRange: 80, high: 20 };
    const result = normalizePercentages(data);

    expect(result.low + result.inRange + result.high).toBeCloseTo(100, 1);
  });

  it("normalizes data that sums to less than 100", () => {
    const data: RangeData = { low: 5, inRange: 35, high: 10 };
    const result = normalizePercentages(data);

    expect(result.low + result.inRange + result.high).toBeCloseTo(100, 1);
  });

  it("returns all zeros for all zeros (no data)", () => {
    const data: RangeData = { low: 0, inRange: 0, high: 0 };
    const result = normalizePercentages(data);

    expect(result).toEqual({ low: 0, inRange: 0, high: 0 });
  });

  it("handles floating point near 100", () => {
    const data: RangeData = { low: 5.001, inRange: 70.002, high: 24.997 };
    const result = normalizePercentages(data);

    // Should return unchanged since it's within 0.01 of 100
    expect(result).toEqual(data);
  });

  it("ensures inRange is never negative", () => {
    // Edge case with extreme rounding
    const data: RangeData = { low: 50.3, inRange: 0, high: 50.3 };
    const result = normalizePercentages(data);
    expect(result.inRange).toBeGreaterThanOrEqual(0);
  });
});

describe("sanitizeRangeData", () => {
  it("returns valid data unchanged", () => {
    const data: RangeData = { low: 10, inRange: 70, high: 20 };
    const result = sanitizeRangeData(data);

    expect(result).toEqual(data);
  });

  it("converts NaN to 0", () => {
    const data: RangeData = { low: NaN, inRange: 70, high: 20 };
    const result = sanitizeRangeData(data);

    expect(result.low).toBe(0);
    expect(result.inRange).toBe(70);
    expect(result.high).toBe(20);
  });

  it("converts Infinity to 0", () => {
    const data: RangeData = { low: Infinity, inRange: 70, high: 20 };
    const result = sanitizeRangeData(data);

    expect(result.low).toBe(0);
  });

  it("converts negative Infinity to 0", () => {
    const data: RangeData = { low: -Infinity, inRange: 70, high: 20 };
    const result = sanitizeRangeData(data);

    expect(result.low).toBe(0);
  });

  it("converts negative numbers to 0", () => {
    const data: RangeData = { low: -5, inRange: 70, high: 20 };
    const result = sanitizeRangeData(data);

    expect(result.low).toBe(0);
  });

  it("caps values at 100", () => {
    const data: RangeData = { low: 150, inRange: 70, high: 20 };
    const result = sanitizeRangeData(data);

    expect(result.low).toBe(100);
  });

  it("handles multiple invalid values", () => {
    const data: RangeData = { low: NaN, inRange: -10, high: Infinity };
    const result = sanitizeRangeData(data);

    expect(result).toEqual({ low: 0, inRange: 0, high: 0 });
  });
});

describe("formatPercentage", () => {
  it("formats 0 as '0%'", () => {
    expect(formatPercentage(0)).toBe("0%");
  });

  it("formats values less than 0.5 as '<1%'", () => {
    expect(formatPercentage(0.1)).toBe("<1%");
    expect(formatPercentage(0.4)).toBe("<1%");
  });

  it("formats 0.5 as '1%' (rounds up)", () => {
    expect(formatPercentage(0.5)).toBe("1%");
  });

  it("formats 0.9 as '1%' (rounds to 1)", () => {
    expect(formatPercentage(0.9)).toBe("1%");
  });

  it("formats 99.4 as '99%' (rounds down)", () => {
    expect(formatPercentage(99.4)).toBe("99%");
  });

  it("formats 99.5 as '>99%' (at threshold)", () => {
    expect(formatPercentage(99.5)).toBe(">99%");
  });

  it("formats 99.9 as '>99%' (above threshold)", () => {
    expect(formatPercentage(99.9)).toBe(">99%");
  });

  it("rounds values to nearest integer", () => {
    expect(formatPercentage(50)).toBe("50%");
    expect(formatPercentage(75.4)).toBe("75%");
    expect(formatPercentage(75.6)).toBe("76%");
  });

  it("formats 100 as '100%'", () => {
    expect(formatPercentage(100)).toBe("100%");
  });
});

describe("getQualityAssessment", () => {
  it("returns Excellent for 70% or higher", () => {
    expect(getQualityAssessment(70)).toEqual({
      label: "Excellent",
      colorClass: "text-green-400",
    });
    expect(getQualityAssessment(85)).toEqual({
      label: "Excellent",
      colorClass: "text-green-400",
    });
    expect(getQualityAssessment(100)).toEqual({
      label: "Excellent",
      colorClass: "text-green-400",
    });
  });

  it("returns Good for 50-69%", () => {
    expect(getQualityAssessment(50)).toEqual({
      label: "Good",
      colorClass: "text-amber-400",
    });
    expect(getQualityAssessment(60)).toEqual({
      label: "Good",
      colorClass: "text-amber-400",
    });
    expect(getQualityAssessment(69)).toEqual({
      label: "Good",
      colorClass: "text-amber-400",
    });
  });

  it("returns Needs Improvement for below 50%", () => {
    expect(getQualityAssessment(49)).toEqual({
      label: "Needs Improvement",
      colorClass: "text-red-400",
    });
    expect(getQualityAssessment(30)).toEqual({
      label: "Needs Improvement",
      colorClass: "text-red-400",
    });
    expect(getQualityAssessment(0)).toEqual({
      label: "Needs Improvement",
      colorClass: "text-red-400",
    });
  });

  it("returns Good for exactly 50%", () => {
    expect(getQualityAssessment(50)).toEqual({
      label: "Good",
      colorClass: "text-amber-400",
    });
  });

  it("returns Needs Improvement for 49.9%", () => {
    expect(getQualityAssessment(49.9)).toEqual({
      label: "Needs Improvement",
      colorClass: "text-red-400",
    });
  });

  it("returns Excellent for exactly 70%", () => {
    expect(getQualityAssessment(70)).toEqual({
      label: "Excellent",
      colorClass: "text-green-400",
    });
  });

  it("returns Good for 69.9%", () => {
    expect(getQualityAssessment(69.9)).toEqual({
      label: "Good",
      colorClass: "text-amber-400",
    });
  });
});

describe("TimeInRangeBar component", () => {
  const defaultData: RangeData = { low: 5, inRange: 78, high: 17 };

  describe("rendering", () => {
    it("renders with data-testid", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });

    it("displays the Time in Range heading", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByText("Time in Range")).toBeInTheDocument();
    });

    it("displays the default period label", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByTestId("period-label")).toHaveTextContent("24 Hours");
    });

    it("displays custom period label", () => {
      render(<TimeInRangeBar data={defaultData} period="7d" />);

      expect(screen.getByTestId("period-label")).toHaveTextContent("7 Days");
    });

    it("displays all period options correctly", () => {
      const periods: TimePeriod[] = ["24h", "3d", "7d", "14d", "30d"];
      const labels = ["24 Hours", "3 Days", "7 Days", "14 Days", "30 Days"];

      periods.forEach((period, index) => {
        const { unmount } = render(
          <TimeInRangeBar data={defaultData} period={period} />
        );
        expect(screen.getByTestId("period-label")).toHaveTextContent(
          labels[index]
        );
        unmount();
      });
    });

    it("displays quality assessment", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByText("Excellent")).toBeInTheDocument();
    });

    it("displays correct quality for Good range", () => {
      render(<TimeInRangeBar data={{ low: 10, inRange: 55, high: 35 }} />);

      expect(screen.getByText("Good")).toBeInTheDocument();
    });

    it("displays correct quality for Needs Improvement range", () => {
      render(<TimeInRangeBar data={{ low: 20, inRange: 40, high: 40 }} />);

      expect(screen.getByText("Needs Improvement")).toBeInTheDocument();
    });

    it("displays target range info", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByText("Target: 70-180 mg/dL")).toBeInTheDocument();
    });

    it("displays custom target range", () => {
      render(
        <TimeInRangeBar data={defaultData} targetRange="80-140 mg/dL" />
      );

      expect(screen.getByText("Target: 80-140 mg/dL")).toBeInTheDocument();
    });
  });

  describe("legend", () => {
    it("shows legend by default", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByTestId("range-legend")).toBeInTheDocument();
    });

    it("hides legend when showLegend is false", () => {
      render(<TimeInRangeBar data={defaultData} showLegend={false} />);

      expect(screen.queryByTestId("range-legend")).not.toBeInTheDocument();
    });

    it("displays legend percentages", () => {
      render(<TimeInRangeBar data={defaultData} />);

      const legend = screen.getByTestId("range-legend");
      expect(legend).toHaveTextContent("Low: 5%");
      expect(legend).toHaveTextContent("In Range: 78%");
      expect(legend).toHaveTextContent("High: 17%");
    });
  });

  describe("accessibility", () => {
    it("has accessible bar with role=img", () => {
      render(<TimeInRangeBar data={defaultData} />);

      const bar = screen.getByRole("img");
      expect(bar).toBeInTheDocument();
    });

    it("has descriptive aria-label on bar", () => {
      render(<TimeInRangeBar data={defaultData} />);

      const bar = screen.getByRole("img");
      expect(bar).toHaveAttribute(
        "aria-label",
        expect.stringContaining("Time in range for 24 Hours")
      );
      expect(bar).toHaveAttribute(
        "aria-label",
        expect.stringContaining("5% below range")
      );
      expect(bar).toHaveAttribute(
        "aria-label",
        expect.stringContaining("78% in range")
      );
      expect(bar).toHaveAttribute(
        "aria-label",
        expect.stringContaining("17% above range")
      );
    });

    it("includes target range in aria-label", () => {
      render(<TimeInRangeBar data={defaultData} targetRange="70-180 mg/dL" />);

      const bar = screen.getByRole("img");
      expect(bar).toHaveAttribute(
        "aria-label",
        expect.stringContaining("Target range: 70-180 mg/dL")
      );
    });
  });

  describe("data handling", () => {
    it("normalizes data that doesn't sum to 100", () => {
      render(<TimeInRangeBar data={{ low: 10, inRange: 40, high: 10 }} />);

      const legend = screen.getByTestId("range-legend");
      // Should normalize to approximately 17%, 67%, 17%
      expect(legend).toBeInTheDocument();
    });

    it("handles all zeros gracefully (no data)", () => {
      render(<TimeInRangeBar data={{ low: 0, inRange: 0, high: 0 }} />);

      const legend = screen.getByTestId("range-legend");
      expect(legend).toHaveTextContent("In Range: 0%");
    });

    it("sanitizes NaN values", () => {
      render(<TimeInRangeBar data={{ low: NaN, inRange: 70, high: 20 }} />);

      // Should render without crashing
      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });

    it("sanitizes negative values", () => {
      render(<TimeInRangeBar data={{ low: -5, inRange: 70, high: 20 }} />);

      // Should render without crashing
      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });
  });

  describe("styling", () => {
    it("applies custom className", () => {
      render(
        <TimeInRangeBar data={defaultData} className="custom-test-class" />
      );

      expect(screen.getByTestId("time-in-range-bar")).toHaveClass(
        "custom-test-class"
      );
    });

    it("has correct base styling classes", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByTestId("time-in-range-bar")).toHaveClass(
        "bg-slate-900",
        "rounded-xl",
        "border",
        "border-slate-800"
      );
    });
  });

  describe("animation", () => {
    it("renders with animation by default", () => {
      render(<TimeInRangeBar data={defaultData} />);

      // Should render the bar (animation is mocked)
      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });

    it("renders without animation when animated is false", () => {
      render(<TimeInRangeBar data={defaultData} animated={false} />);

      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });
  });

  describe("reduced motion", () => {
    it("respects prefers-reduced-motion preference", () => {
      mockUseReducedMotion.mockReturnValue(true);
      const data: RangeData = { low: 5, inRange: 78, high: 17 };
      render(<TimeInRangeBar data={data} animated={true} />);

      // Component should still render correctly
      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });
  });

  describe("segment labels", () => {
    it("shows labels on segments >= 10%", () => {
      render(
        <TimeInRangeBar
          data={{ low: 15, inRange: 70, high: 15 }}
          showLabels={true}
        />
      );

      // Segments should have their percentage labels
      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });

    it("hides labels when showLabels is false", () => {
      render(
        <TimeInRangeBar
          data={{ low: 15, inRange: 70, high: 15 }}
          showLabels={false}
        />
      );

      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });
  });

  describe("loading state", () => {
    it("shows skeleton when isLoading is true", () => {
      const data: RangeData = { low: 5, inRange: 78, high: 17 };
      render(<TimeInRangeBar data={data} isLoading={true} />);

      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
      expect(screen.getByRole("region")).toHaveAttribute("aria-busy", "true");
    });

    it("does not show legend when loading", () => {
      const data: RangeData = { low: 5, inRange: 78, high: 17 };
      render(<TimeInRangeBar data={data} isLoading={true} />);

      expect(screen.queryByTestId("range-legend")).not.toBeInTheDocument();
    });

    it("has accessible loading label", () => {
      const data: RangeData = { low: 5, inRange: 78, high: 17 };
      render(<TimeInRangeBar data={data} isLoading={true} />);

      expect(screen.getByRole("region")).toHaveAttribute(
        "aria-label",
        "Loading time in range data"
      );
    });
  });

  describe("period selector", () => {
    it("renders period selector when onPeriodChange is provided", () => {
      const onChange = jest.fn();
      render(
        <TimeInRangeBar data={defaultData} onPeriodChange={onChange} />
      );

      expect(screen.getByTestId("period-selector")).toBeInTheDocument();
      expect(screen.queryByTestId("period-label")).not.toBeInTheDocument();
    });

    it("renders static label when onPeriodChange is not provided", () => {
      render(<TimeInRangeBar data={defaultData} />);

      expect(screen.getByTestId("period-label")).toBeInTheDocument();
      expect(screen.queryByTestId("period-selector")).not.toBeInTheDocument();
    });

    it("calls onPeriodChange when a period button is clicked", () => {
      const onChange = jest.fn();
      render(
        <TimeInRangeBar data={defaultData} period="24h" onPeriodChange={onChange} />
      );

      fireEvent.click(screen.getByRole("radio", { name: "7D" }));
      expect(onChange).toHaveBeenCalledWith("7d");
    });

    it("marks the current period as checked", () => {
      const onChange = jest.fn();
      render(
        <TimeInRangeBar data={defaultData} period="7d" onPeriodChange={onChange} />
      );

      expect(screen.getByRole("radio", { name: "7D" })).toHaveAttribute(
        "aria-checked",
        "true"
      );
      expect(screen.getByRole("radio", { name: "24H" })).toHaveAttribute(
        "aria-checked",
        "false"
      );
    });

    it("renders all five period options", () => {
      const onChange = jest.fn();
      render(
        <TimeInRangeBar data={defaultData} onPeriodChange={onChange} />
      );

      const radios = screen.getAllByRole("radio");
      expect(radios).toHaveLength(5);
      expect(radios.map((r) => r.textContent)).toEqual([
        "24H",
        "3D",
        "7D",
        "14D",
        "30D",
      ]);
    });
  });

  describe("exports", () => {
    it("default export works", () => {
      render(<TimeInRangeBarDefault data={defaultData} />);

      expect(screen.getByTestId("time-in-range-bar")).toBeInTheDocument();
    });
  });
});
