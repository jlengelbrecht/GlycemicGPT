/**
 * Tests for the Data Freshness Indicator component.
 *
 * Story 3.6: Data Freshness Display
 */

import { render, screen, fireEvent } from "@testing-library/react";
import {
  DataFreshnessIndicator,
  DataFreshnessIndicatorCompact,
  getFreshnessLevel,
  formatTimeDiff,
  formatTimestamp,
} from "../src/components/data-freshness-indicator";

// Mock framer-motion to avoid animation issues in tests
jest.mock("framer-motion", () => ({
  motion: {
    div: ({
      children,
      ...props
    }: {
      children: React.ReactNode;
      [key: string]: unknown;
    }) => <div {...props}>{children}</div>,
  },
}));

describe("getFreshnessLevel", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-02-07T12:00:00Z"));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('returns "fresh" when data is less than 5 minutes old', () => {
    const lastUpdated = new Date("2026-02-07T11:57:00Z").toISOString();
    expect(getFreshnessLevel(lastUpdated, 5, 10)).toBe("fresh");
  });

  it('returns "stale" when data is 5-10 minutes old', () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    expect(getFreshnessLevel(lastUpdated, 5, 10)).toBe("stale");
  });

  it('returns "critical" when data is more than 10 minutes old', () => {
    const lastUpdated = new Date("2026-02-07T11:45:00Z").toISOString();
    expect(getFreshnessLevel(lastUpdated, 5, 10)).toBe("critical");
  });

  it('returns "critical" when lastUpdated is null', () => {
    expect(getFreshnessLevel(null, 5, 10)).toBe("critical");
  });

  it("uses custom thresholds correctly", () => {
    // 8 minutes ago - should be "fresh" with 10min threshold
    const lastUpdated = new Date("2026-02-07T11:52:00Z").toISOString();
    expect(getFreshnessLevel(lastUpdated, 10, 20)).toBe("fresh");

    // Same time should be "stale" with 5min threshold
    expect(getFreshnessLevel(lastUpdated, 5, 10)).toBe("stale");
  });
});

describe("formatTimeDiff", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-02-07T12:00:00Z"));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('returns "Just now" for data less than 1 minute old', () => {
    const lastUpdated = new Date("2026-02-07T11:59:30Z").toISOString();
    expect(formatTimeDiff(lastUpdated)).toBe("Just now");
  });

  it('returns "1 min ago" for data exactly 1 minute old', () => {
    const lastUpdated = new Date("2026-02-07T11:59:00Z").toISOString();
    expect(formatTimeDiff(lastUpdated)).toBe("1 min ago");
  });

  it('returns "X min ago" for data less than 1 hour old', () => {
    const lastUpdated = new Date("2026-02-07T11:45:00Z").toISOString();
    expect(formatTimeDiff(lastUpdated)).toBe("15 min ago");
  });

  it('returns "1 hour ago" for data exactly 1 hour old', () => {
    const lastUpdated = new Date("2026-02-07T11:00:00Z").toISOString();
    expect(formatTimeDiff(lastUpdated)).toBe("1 hour ago");
  });

  it('returns "X hours ago" for data less than 24 hours old', () => {
    const lastUpdated = new Date("2026-02-07T09:00:00Z").toISOString();
    expect(formatTimeDiff(lastUpdated)).toBe("3 hours ago");
  });

  it('returns "No data" when lastUpdated is null', () => {
    expect(formatTimeDiff(null)).toBe("No data");
  });
});

describe("formatTimestamp", () => {
  it("formats timestamp correctly", () => {
    const lastUpdated = new Date("2026-02-07T14:30:00Z").toISOString();
    const result = formatTimestamp(lastUpdated);
    // Result depends on locale, but should contain hour and minute
    expect(result).toMatch(/\d{1,2}:\d{2}/);
  });

  it('returns "Unknown" when lastUpdated is null', () => {
    expect(formatTimestamp(null)).toBe("Unknown");
  });
});

describe("DataFreshnessIndicator", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-02-07T12:00:00Z"));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders with fresh data and green indicator", () => {
    const lastUpdated = new Date("2026-02-07T11:58:00Z").toISOString();
    render(<DataFreshnessIndicator lastUpdated={lastUpdated} />);

    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: fresh"
    );
    expect(screen.getByText("2 min ago")).toBeInTheDocument();
  });

  it("renders with stale data and yellow indicator", () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    render(<DataFreshnessIndicator lastUpdated={lastUpdated} />);

    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: stale"
    );
  });

  it("renders with critical data and red indicator with warning", () => {
    const lastUpdated = new Date("2026-02-07T11:45:00Z").toISOString();
    render(<DataFreshnessIndicator lastUpdated={lastUpdated} />);

    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: critical"
    );
    expect(screen.getByText("Data may be stale")).toBeInTheDocument();
  });

  it("renders label when provided", () => {
    const lastUpdated = new Date("2026-02-07T11:58:00Z").toISOString();
    render(<DataFreshnessIndicator lastUpdated={lastUpdated} label="CGM" />);

    expect(screen.getByText("CGM:")).toBeInTheDocument();
  });

  it("shows refresh button for stale data when onRefresh provided", () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    const onRefresh = jest.fn();
    render(
      <DataFreshnessIndicator lastUpdated={lastUpdated} onRefresh={onRefresh} />
    );

    const refreshButton = screen.getByRole("button", { name: "Refresh data" });
    expect(refreshButton).toBeInTheDocument();
  });

  it("does not show refresh button for fresh data", () => {
    const lastUpdated = new Date("2026-02-07T11:58:00Z").toISOString();
    const onRefresh = jest.fn();
    render(
      <DataFreshnessIndicator lastUpdated={lastUpdated} onRefresh={onRefresh} />
    );

    expect(
      screen.queryByRole("button", { name: "Refresh data" })
    ).not.toBeInTheDocument();
  });

  it("calls onRefresh when refresh button is clicked", () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    const onRefresh = jest.fn();
    render(
      <DataFreshnessIndicator lastUpdated={lastUpdated} onRefresh={onRefresh} />
    );

    const refreshButton = screen.getByRole("button", { name: "Refresh data" });
    fireEvent.click(refreshButton);

    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it("disables refresh button when isRefreshing is true", () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    const onRefresh = jest.fn();
    render(
      <DataFreshnessIndicator
        lastUpdated={lastUpdated}
        onRefresh={onRefresh}
        isRefreshing={true}
      />
    );

    const refreshButton = screen.getByRole("button", { name: "Refresh data" });
    expect(refreshButton).toBeDisabled();

    fireEvent.click(refreshButton);
    expect(onRefresh).not.toHaveBeenCalled();
  });

  it("handles null lastUpdated as critical", () => {
    render(<DataFreshnessIndicator lastUpdated={null} />);

    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: critical"
    );
    expect(screen.getByText("Data may be stale")).toBeInTheDocument();
  });

  it("uses custom thresholds", () => {
    // 8 minutes ago
    const lastUpdated = new Date("2026-02-07T11:52:00Z").toISOString();

    // With default thresholds (5, 10), this should be "stale"
    const { rerender } = render(
      <DataFreshnessIndicator lastUpdated={lastUpdated} />
    );
    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: stale"
    );

    // With custom thresholds (10, 20), this should be "fresh"
    rerender(
      <DataFreshnessIndicator
        lastUpdated={lastUpdated}
        staleThresholdMinutes={10}
        criticalThresholdMinutes={20}
      />
    );
    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: fresh"
    );
  });
});

describe("DataFreshnessIndicatorCompact", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date("2026-02-07T12:00:00Z"));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders compact indicator for fresh data", () => {
    const lastUpdated = new Date("2026-02-07T11:58:00Z").toISOString();
    render(<DataFreshnessIndicatorCompact lastUpdated={lastUpdated} />);

    expect(screen.getByRole("status")).toHaveAttribute(
      "aria-label",
      "Data freshness: fresh"
    );
    // Compact version should not show text
    expect(screen.queryByText("2 min ago")).not.toBeInTheDocument();
  });

  it("shows refresh button for stale data when onRefresh provided", () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    const onRefresh = jest.fn();
    render(
      <DataFreshnessIndicatorCompact
        lastUpdated={lastUpdated}
        onRefresh={onRefresh}
      />
    );

    const refreshButton = screen.getByRole("button", { name: "Refresh data" });
    expect(refreshButton).toBeInTheDocument();
  });

  it("calls onRefresh when refresh button is clicked", () => {
    const lastUpdated = new Date("2026-02-07T11:53:00Z").toISOString();
    const onRefresh = jest.fn();
    render(
      <DataFreshnessIndicatorCompact
        lastUpdated={lastUpdated}
        onRefresh={onRefresh}
      />
    );

    const refreshButton = screen.getByRole("button", { name: "Refresh data" });
    fireEvent.click(refreshButton);

    expect(onRefresh).toHaveBeenCalledTimes(1);
  });
});
