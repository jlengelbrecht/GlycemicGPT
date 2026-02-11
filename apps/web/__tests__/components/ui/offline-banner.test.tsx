/**
 * Tests for the OfflineBanner component.
 *
 * Story 12.4: Graceful Offline/Disconnected State for All Settings
 */

import { render, screen, fireEvent } from "@testing-library/react";
import { OfflineBanner } from "../../../src/components/ui/offline-banner";

describe("OfflineBanner", () => {
  it("renders default message when no custom message provided", () => {
    const onRetry = jest.fn();
    render(<OfflineBanner onRetry={onRetry} />);

    expect(
      screen.getByText("Unable to connect to server. Showing default values.")
    ).toBeInTheDocument();
  });

  it("renders custom message when provided", () => {
    const onRetry = jest.fn();
    render(
      <OfflineBanner
        onRetry={onRetry}
        message="Unable to connect to server. Profile management is unavailable."
      />
    );

    expect(
      screen.getByText(
        "Unable to connect to server. Profile management is unavailable."
      )
    ).toBeInTheDocument();
  });

  it("renders Retry Connection button", () => {
    const onRetry = jest.fn();
    render(<OfflineBanner onRetry={onRetry} />);

    expect(
      screen.getByRole("button", { name: /retry connection/i })
    ).toBeInTheDocument();
  });

  it("calls onRetry when Retry Connection is clicked", () => {
    const onRetry = jest.fn();
    render(<OfflineBanner onRetry={onRetry} />);

    fireEvent.click(
      screen.getByRole("button", { name: /retry connection/i })
    );
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("shows Retrying... text when isRetrying is true", () => {
    const onRetry = jest.fn();
    render(<OfflineBanner onRetry={onRetry} isRetrying />);

    expect(screen.getByText("Retrying...")).toBeInTheDocument();
    expect(screen.queryByText("Retry Connection")).not.toBeInTheDocument();
  });

  it("disables retry button when isRetrying is true", () => {
    const onRetry = jest.fn();
    render(<OfflineBanner onRetry={onRetry} isRetrying />);

    const button = screen.getByRole("button", { name: /retrying/i });
    expect(button).toBeDisabled();
  });

  it("has alert role for accessibility", () => {
    const onRetry = jest.fn();
    render(<OfflineBanner onRetry={onRetry} />);

    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("uses amber color scheme for warning styling", () => {
    const onRetry = jest.fn();
    const { container } = render(<OfflineBanner onRetry={onRetry} />);

    const alertDiv = container.firstElementChild;
    expect(alertDiv?.className).toContain("amber");
  });
});
