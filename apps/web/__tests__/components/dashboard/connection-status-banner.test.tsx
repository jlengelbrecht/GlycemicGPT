/**
 * ConnectionStatusBanner Component Tests
 *
 * Story 4.5: Real-Time Updates via SSE
 */

import { render, screen, fireEvent } from "@testing-library/react";

import { ConnectionStatusBanner } from "@/components/dashboard";

describe("ConnectionStatusBanner", () => {
  describe("Visibility", () => {
    it("should not render when not reconnecting and no error", () => {
      const { container } = render(
        <ConnectionStatusBanner isReconnecting={false} />
      );
      expect(container.firstChild).toBeNull();
    });

    it("should render when reconnecting", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      expect(screen.getByTestId("connection-status-banner")).toBeInTheDocument();
    });

    it("should render when there is an error", () => {
      render(<ConnectionStatusBanner isReconnecting={false} hasError={true} />);
      expect(screen.getByTestId("connection-status-banner")).toBeInTheDocument();
    });
  });

  describe("Reconnecting State", () => {
    it("should display reconnecting message", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      expect(
        screen.getByText("Live updates paused. Reconnecting...")
      ).toBeInTheDocument();
    });

    it("should have amber/warning styling", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      const banner = screen.getByTestId("connection-status-banner");
      expect(banner).toHaveClass("bg-amber-500/20");
      expect(banner).toHaveClass("border-amber-500");
    });

    it("should show spinning refresh icon with motion-safe animation", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      const banner = screen.getByTestId("connection-status-banner");
      // The RefreshCw icon should have motion-safe:animate-spin class for accessibility
      const icon = banner.querySelector("svg");
      expect(icon).toHaveClass("motion-safe:animate-spin");
    });
  });

  describe("Error State", () => {
    it("should display error message", () => {
      render(
        <ConnectionStatusBanner
          isReconnecting={false}
          hasError={true}
          errorMessage="Connection failed"
        />
      );
      expect(screen.getByText("Connection failed")).toBeInTheDocument();
    });

    it("should display default error message when no errorMessage provided", () => {
      render(
        <ConnectionStatusBanner isReconnecting={false} hasError={true} />
      );
      expect(
        screen.getByText("Connection lost. Unable to receive live updates.")
      ).toBeInTheDocument();
    });

    it("should have red/error styling", () => {
      render(
        <ConnectionStatusBanner isReconnecting={false} hasError={true} />
      );
      const banner = screen.getByTestId("connection-status-banner");
      expect(banner).toHaveClass("bg-red-500/20");
      expect(banner).toHaveClass("border-red-500");
    });

    it("should show retry button on error", () => {
      const onReconnect = jest.fn();
      render(
        <ConnectionStatusBanner
          isReconnecting={false}
          hasError={true}
          onReconnect={onReconnect}
        />
      );
      expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
    });

    it("should call onReconnect when retry button is clicked", () => {
      const onReconnect = jest.fn();
      render(
        <ConnectionStatusBanner
          isReconnecting={false}
          hasError={true}
          onReconnect={onReconnect}
        />
      );
      fireEvent.click(screen.getByRole("button", { name: /retry/i }));
      expect(onReconnect).toHaveBeenCalledTimes(1);
    });

    it("should not show retry button when reconnecting", () => {
      const onReconnect = jest.fn();
      render(
        <ConnectionStatusBanner
          isReconnecting={true}
          hasError={true}
          onReconnect={onReconnect}
        />
      );
      expect(screen.queryByRole("button", { name: /retry/i })).not.toBeInTheDocument();
    });
  });

  describe("Dismissible", () => {
    it("should not show dismiss button by default", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      expect(
        screen.queryByRole("button", { name: /dismiss/i })
      ).not.toBeInTheDocument();
    });

    it("should show dismiss button when dismissible is true", () => {
      render(<ConnectionStatusBanner isReconnecting={true} dismissible={true} />);
      expect(
        screen.getByRole("button", { name: /dismiss/i })
      ).toBeInTheDocument();
    });

    it("should hide banner when dismiss button is clicked", () => {
      render(<ConnectionStatusBanner isReconnecting={true} dismissible={true} />);
      fireEvent.click(screen.getByRole("button", { name: /dismiss/i }));
      expect(screen.queryByTestId("connection-status-banner")).not.toBeInTheDocument();
    });
  });

  describe("Accessibility", () => {
    it("should have role=alert", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    it("should have aria-live=polite", () => {
      render(<ConnectionStatusBanner isReconnecting={true} />);
      const banner = screen.getByTestId("connection-status-banner");
      expect(banner).toHaveAttribute("aria-live", "polite");
    });

    it("should have accessible dismiss button label", () => {
      render(<ConnectionStatusBanner isReconnecting={true} dismissible={true} />);
      expect(
        screen.getByRole("button", { name: "Dismiss notification" })
      ).toBeInTheDocument();
    });
  });

  describe("Custom className", () => {
    it("should apply custom className", () => {
      render(
        <ConnectionStatusBanner
          isReconnecting={true}
          className="custom-class"
        />
      );
      const banner = screen.getByTestId("connection-status-banner");
      expect(banner).toHaveClass("custom-class");
    });
  });
});
