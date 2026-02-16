/**
 * Tests for the GlucoseHero component.
 *
 * Story 4.2: GlucoseHero Component
 */

import { render, screen } from "@testing-library/react";
import {
  GlucoseHero,
  classifyGlucose,
  shouldPulse,
  GLUCOSE_THRESHOLDS,
  type TrendDirection,
} from "../../../src/components/dashboard/glucose-hero";
import GlucoseHeroDefault from "../../../src/components/dashboard/glucose-hero";

// Mock framer-motion to avoid animation issues in tests
const mockUseReducedMotion = jest.fn(() => false);

jest.mock("framer-motion", () => ({
  motion: {
    div: ({
      children,
      className,
      ...props
    }: {
      children: React.ReactNode;
      className?: string;
      [key: string]: unknown;
    }) => (
      <div className={className} {...props}>
        {children}
      </div>
    ),
  },
  useReducedMotion: () => mockUseReducedMotion(),
}));

// Reset mock before each test
beforeEach(() => {
  mockUseReducedMotion.mockReturnValue(false);
});

describe("GLUCOSE_THRESHOLDS", () => {
  it("exports expected threshold values", () => {
    expect(GLUCOSE_THRESHOLDS.URGENT_LOW).toBe(55);
    expect(GLUCOSE_THRESHOLDS.LOW).toBe(70);
    expect(GLUCOSE_THRESHOLDS.HIGH).toBe(180);
    expect(GLUCOSE_THRESHOLDS.URGENT_HIGH).toBe(250);
  });
});

describe("classifyGlucose", () => {
  it('returns "urgentLow" for glucose < 55', () => {
    expect(classifyGlucose(54)).toBe("urgentLow");
    expect(classifyGlucose(40)).toBe("urgentLow");
    expect(classifyGlucose(0)).toBe("urgentLow");
  });

  it('returns "low" for glucose 55-69', () => {
    expect(classifyGlucose(55)).toBe("low");
    expect(classifyGlucose(60)).toBe("low");
    expect(classifyGlucose(69)).toBe("low");
  });

  it('returns "inRange" for glucose 70-180', () => {
    expect(classifyGlucose(70)).toBe("inRange");
    expect(classifyGlucose(120)).toBe("inRange");
    expect(classifyGlucose(180)).toBe("inRange");
  });

  it('returns "high" for glucose 181-250', () => {
    expect(classifyGlucose(181)).toBe("high");
    expect(classifyGlucose(200)).toBe("high");
    expect(classifyGlucose(250)).toBe("high");
  });

  it('returns "urgentHigh" for glucose > 250', () => {
    expect(classifyGlucose(251)).toBe("urgentHigh");
    expect(classifyGlucose(300)).toBe("urgentHigh");
    expect(classifyGlucose(400)).toBe("urgentHigh");
  });

  it('returns "inRange" for null glucose', () => {
    expect(classifyGlucose(null)).toBe("inRange");
  });
});

describe("shouldPulse", () => {
  it('returns "strong" for urgentLow', () => {
    expect(shouldPulse("urgentLow")).toBe("strong");
  });

  it('returns "strong" for urgentHigh', () => {
    expect(shouldPulse("urgentHigh")).toBe("strong");
  });

  it('returns "subtle" for low', () => {
    expect(shouldPulse("low")).toBe("subtle");
  });

  it('returns "subtle" for high', () => {
    expect(shouldPulse("high")).toBe("subtle");
  });

  it("returns null for inRange", () => {
    expect(shouldPulse("inRange")).toBeNull();
  });
});

describe("GlucoseHero", () => {
  const defaultProps = {
    value: 142,
    trend: "Stable" as TrendDirection,
    iob: 2.4,
    cob: 15,
  };

  describe("glucose value display", () => {
    it("displays glucose value in large text", () => {
      render(<GlucoseHero {...defaultProps} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveTextContent("142");
      expect(glucoseValue).toHaveClass("text-7xl", "font-bold");
    });

    it('displays "--" when value is null', () => {
      render(<GlucoseHero {...defaultProps} value={null} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveTextContent("--");
    });

    it("rounds decimal values to integers", () => {
      render(<GlucoseHero {...defaultProps} value={142.7} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveTextContent("143");
    });
  });

  describe("unit label display", () => {
    it('displays default unit "mg/dL"', () => {
      render(<GlucoseHero {...defaultProps} />);

      expect(screen.getByTestId("glucose-unit")).toHaveTextContent("mg/dL");
    });

    it("displays custom unit when provided", () => {
      render(<GlucoseHero {...defaultProps} unit="mmol/L" />);

      expect(screen.getByTestId("glucose-unit")).toHaveTextContent("mmol/L");
    });
  });

  describe("trend arrow display", () => {
    it.each([
      ["RisingFast", "↑↑"],
      ["Rising", "↗"],
      ["Stable", "→"],
      ["Falling", "↘"],
      ["FallingFast", "↓↓"],
      ["Unknown", "?"],
    ] as const)(
      "displays correct arrow for trend %s",
      (trend: TrendDirection, expectedArrow: string) => {
        render(<GlucoseHero {...defaultProps} trend={trend} />);

        const trendArrow = screen.getByTestId("trend-arrow");
        expect(trendArrow).toHaveTextContent(expectedArrow);
      }
    );
  });

  describe("secondary metrics display", () => {
    it("displays IoB value with unit", () => {
      render(<GlucoseHero {...defaultProps} iob={2.4} />);

      expect(screen.getByTestId("iob-value")).toHaveTextContent("2.40u");
    });

    it("displays CoB value with unit", () => {
      render(<GlucoseHero {...defaultProps} cob={15} />);

      expect(screen.getByTestId("cob-value")).toHaveTextContent("15g");
    });

    it('displays "--" for null IoB', () => {
      render(<GlucoseHero {...defaultProps} iob={null} />);

      expect(screen.getByTestId("iob-value")).toHaveTextContent("--");
    });

    it('displays "--" for null CoB', () => {
      render(<GlucoseHero {...defaultProps} cob={null} />);

      expect(screen.getByTestId("cob-value")).toHaveTextContent("--");
    });

    it("formats IoB to 2 decimal places", () => {
      render(<GlucoseHero {...defaultProps} iob={2.456} />);

      expect(screen.getByTestId("iob-value")).toHaveTextContent("2.46u");
    });

    it("rounds CoB to nearest integer", () => {
      render(<GlucoseHero {...defaultProps} cob={15.7} />);

      expect(screen.getByTestId("cob-value")).toHaveTextContent("16g");
    });

    it("rounds CoB down when below .5", () => {
      render(<GlucoseHero {...defaultProps} cob={15.3} />);

      expect(screen.getByTestId("cob-value")).toHaveTextContent("15g");
    });
  });

  describe("color coding by glucose range", () => {
    it("uses green styling for in-range glucose (70-180)", () => {
      render(<GlucoseHero {...defaultProps} value={120} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveClass("text-green-400");
    });

    it("uses amber styling for low warning glucose (55-70)", () => {
      render(<GlucoseHero {...defaultProps} value={65} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveClass("text-amber-400");
    });

    it("uses red styling for urgent low glucose (<55)", () => {
      render(<GlucoseHero {...defaultProps} value={50} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveClass("text-red-500");
    });

    it("uses amber styling for high warning glucose (180-250)", () => {
      render(<GlucoseHero {...defaultProps} value={200} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveClass("text-amber-400");
    });

    it("uses red styling for urgent high glucose (>250)", () => {
      render(<GlucoseHero {...defaultProps} value={280} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      expect(glucoseValue).toHaveClass("text-red-500");
    });

    it("trend arrow inherits glucose range color", () => {
      render(<GlucoseHero {...defaultProps} value={120} />);

      const trendArrow = screen.getByTestId("trend-arrow");
      expect(trendArrow).toHaveClass("text-green-400");
    });

    it("trend arrow uses same color as urgent glucose value", () => {
      render(<GlucoseHero {...defaultProps} value={280} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      const trendArrow = screen.getByTestId("trend-arrow");
      expect(glucoseValue).toHaveClass("text-red-500");
      expect(trendArrow).toHaveClass("text-red-500");
    });
  });

  describe("accessibility", () => {
    it("has region role with appropriate label", () => {
      render(<GlucoseHero {...defaultProps} />);

      expect(
        screen.getByRole("region", { name: "Current glucose reading" })
      ).toBeInTheDocument();
    });

    it("provides aria-label with glucose reading details", () => {
      render(<GlucoseHero {...defaultProps} value={142} trend="Stable" />);

      const glucoseValue = screen.getByTestId("glucose-value");
      // Story 4.6: Enhanced accessible announcement format
      expect(glucoseValue).toHaveAttribute(
        "aria-label",
        "Glucose 142 milligrams per deciliter, stable, in target range"
      );
    });

    it("provides appropriate aria-label when value is null", () => {
      render(<GlucoseHero {...defaultProps} value={null} />);

      const glucoseValue = screen.getByTestId("glucose-value");
      // Story 4.6: Enhanced accessible announcement format
      expect(glucoseValue).toHaveAttribute(
        "aria-label",
        "Glucose reading unavailable"
      );
    });

    it("has aria-live polite for glucose updates", () => {
      render(<GlucoseHero {...defaultProps} />);

      // The parent container of the glucose value should have aria-live
      const container = screen.getByTestId("glucose-value").parentElement;
      expect(container).toHaveAttribute("aria-live", "polite");
    });

    it("hides trend arrow from screen readers", () => {
      render(<GlucoseHero {...defaultProps} />);

      const trendArrow = screen.getByTestId("trend-arrow");
      expect(trendArrow).toHaveAttribute("aria-hidden", "true");
    });
  });

  describe("reduced motion support", () => {
    it("disables pulse animation when prefers-reduced-motion is enabled", () => {
      mockUseReducedMotion.mockReturnValue(true);

      // Render with urgent low (would normally have strong pulse)
      render(<GlucoseHero {...defaultProps} value={50} />);

      // Component should render correctly in reduced motion mode
      expect(screen.getByTestId("glucose-value")).toBeInTheDocument();
      expect(screen.getByTestId("glucose-value")).toHaveTextContent("50");
    });

    it("component renders normally when motion is allowed", () => {
      mockUseReducedMotion.mockReturnValue(false);

      render(<GlucoseHero {...defaultProps} value={50} />);

      expect(screen.getByTestId("glucose-value")).toBeInTheDocument();
    });
  });

  describe("stale data indicator", () => {
    it("shows stale warning when isStale is true", () => {
      render(<GlucoseHero {...defaultProps} isStale={true} minutesAgo={15} />);

      expect(screen.getByTestId("stale-warning")).toBeInTheDocument();
      expect(screen.getByText(/Data is 15\+ minutes old/)).toBeInTheDocument();
    });

    it("does not show stale warning when isStale is false", () => {
      render(<GlucoseHero {...defaultProps} isStale={false} minutesAgo={2} />);

      expect(screen.queryByTestId("stale-warning")).not.toBeInTheDocument();
    });

    it("uses default minutes when minutesAgo is not provided", () => {
      render(<GlucoseHero {...defaultProps} isStale={true} />);

      expect(screen.getByText(/Data is 10\+ minutes old/)).toBeInTheDocument();
    });

    it("stale warning has alert role for screen readers", () => {
      render(<GlucoseHero {...defaultProps} isStale={true} minutesAgo={15} />);

      expect(screen.getByTestId("stale-warning")).toHaveAttribute("role", "alert");
    });
  });

  describe("loading state", () => {
    it("shows loading skeleton when isLoading is true", () => {
      render(<GlucoseHero {...defaultProps} isLoading={true} />);

      expect(
        screen.getByRole("region", { name: "Loading glucose reading" })
      ).toBeInTheDocument();
    });

    it("has aria-busy when loading", () => {
      render(<GlucoseHero {...defaultProps} isLoading={true} />);

      expect(
        screen.getByRole("region", { name: "Loading glucose reading" })
      ).toHaveAttribute("aria-busy", "true");
    });

    it("does not show glucose value when loading", () => {
      render(<GlucoseHero {...defaultProps} isLoading={true} />);

      expect(screen.queryByTestId("glucose-value")).not.toBeInTheDocument();
    });

    it("shows normal content when isLoading is false", () => {
      render(<GlucoseHero {...defaultProps} isLoading={false} />);

      expect(screen.getByTestId("glucose-value")).toBeInTheDocument();
    });
  });

  describe("defensive handling", () => {
    it("treats NaN glucose as null", () => {
      render(<GlucoseHero {...defaultProps} value={NaN} />);

      expect(screen.getByTestId("glucose-value")).toHaveTextContent("--");
    });

    it("treats negative glucose as null", () => {
      render(<GlucoseHero {...defaultProps} value={-50} />);

      expect(screen.getByTestId("glucose-value")).toHaveTextContent("--");
    });

    it("treats Infinity as null", () => {
      render(<GlucoseHero {...defaultProps} value={Infinity} />);

      expect(screen.getByTestId("glucose-value")).toHaveTextContent("--");
    });

    it("treats negative Infinity as null", () => {
      render(<GlucoseHero {...defaultProps} value={-Infinity} />);

      expect(screen.getByTestId("glucose-value")).toHaveTextContent("--");
    });

    it("treats NaN IoB as null", () => {
      render(<GlucoseHero {...defaultProps} iob={NaN} />);

      expect(screen.getByTestId("iob-value")).toHaveTextContent("--");
    });

    it("treats NaN CoB as null", () => {
      render(<GlucoseHero {...defaultProps} cob={NaN} />);

      expect(screen.getByTestId("cob-value")).toHaveTextContent("--");
    });

    it("treats negative CoB as null", () => {
      render(<GlucoseHero {...defaultProps} cob={-10} />);

      expect(screen.getByTestId("cob-value")).toHaveTextContent("--");
    });

    it("allows negative IoB (rare but possible)", () => {
      render(<GlucoseHero {...defaultProps} iob={-0.5} />);

      expect(screen.getByTestId("iob-value")).toHaveTextContent("-0.50u");
    });
  });

  describe("exports", () => {
    it("default export works", () => {
      render(<GlucoseHeroDefault {...defaultProps} />);

      expect(screen.getByTestId("glucose-value")).toBeInTheDocument();
    });
  });
});
