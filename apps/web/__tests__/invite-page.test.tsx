/**
 * Story 15.6: Invite Page Link Tests
 *
 * Tests that the invite accept page links point to /login instead of /.
 */

import { render, screen, waitFor, fireEvent } from "@testing-library/react";

// Mock next/link
jest.mock("next/link", () => {
  return function MockLink({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) {
    return (
      <a href={href} {...props}>
        {children}
      </a>
    );
  };
});

// Mock next/navigation
const mockPush = jest.fn();
jest.mock("next/navigation", () => ({
  useParams: () => ({ token: "test-token-123" }),
  useRouter: () => ({ push: mockPush }),
}));

// Mock lucide-react
jest.mock("lucide-react", () => ({
  UserPlus: ({ className }: { className?: string }) => (
    <span data-testid="user-plus-icon" className={className} />
  ),
  Loader2: ({ className }: { className?: string }) => (
    <span data-testid="loader-icon" className={className} />
  ),
  AlertTriangle: ({ className }: { className?: string }) => (
    <span data-testid="alert-icon" className={className} />
  ),
  CheckCircle: ({ className }: { className?: string }) => (
    <span data-testid="check-icon" className={className} />
  ),
  XCircle: ({ className }: { className?: string }) => (
    <span data-testid="x-icon" className={className} />
  ),
  Clock: ({ className }: { className?: string }) => (
    <span data-testid="clock-icon" className={className} />
  ),
  Eye: ({ className }: { className?: string }) => (
    <span data-testid="eye-icon" className={className} />
  ),
  EyeOff: ({ className }: { className?: string }) => (
    <span data-testid="eye-off-icon" className={className} />
  ),
}));

// Mock API functions
const mockGetInvitationDetails = jest.fn();
const mockAcceptCaregiverInvitation = jest.fn();
jest.mock("@/lib/api", () => ({
  getInvitationDetails: (...args: unknown[]) =>
    mockGetInvitationDetails(...args),
  acceptCaregiverInvitation: (...args: unknown[]) =>
    mockAcceptCaregiverInvitation(...args),
}));

import InviteAcceptPage from "@/app/invite/[token]/page";

beforeEach(() => {
  jest.clearAllMocks();
  jest.useFakeTimers();
});

afterEach(() => {
  jest.useRealTimers();
});

describe("Invite Page - Log in link", () => {
  it("shows 'Log in' link pointing to /login on the registration form", async () => {
    mockGetInvitationDetails.mockResolvedValue({
      patient_email: "patient@example.com",
      status: "pending",
      expires_at: new Date(Date.now() + 86400000).toISOString(),
    });

    render(<InviteAcceptPage />);

    await waitFor(() => {
      expect(screen.getByText(/already have a caregiver account/i)).toBeInTheDocument();
    });

    const loginLink = screen.getByRole("link", { name: "Log in" });
    expect(loginLink).toHaveAttribute("href", "/login");
  });
});

describe("Invite Page - Error state links", () => {
  it("shows 'Go to Home' link on error state pointing to /", async () => {
    mockGetInvitationDetails.mockRejectedValue(new Error("Not found"));

    render(<InviteAcceptPage />);

    await waitFor(() => {
      expect(screen.getByText("Invalid Invitation")).toBeInTheDocument();
    });

    const homeLink = screen.getByRole("link", { name: "Go to Home" });
    expect(homeLink).toHaveAttribute("href", "/");
  });
});

describe("Invite Page - Post-accept redirect", () => {
  it("redirects to /login after successful form submission", async () => {
    mockGetInvitationDetails.mockResolvedValue({
      patient_email: "patient@example.com",
      status: "pending",
      expires_at: new Date(Date.now() + 86400000).toISOString(),
    });
    mockAcceptCaregiverInvitation.mockResolvedValue({
      message: "Success",
      user_id: "123",
    });

    render(<InviteAcceptPage />);

    // Wait for form to load
    await waitFor(() => {
      expect(screen.getByLabelText("Email Address")).toBeInTheDocument();
    });

    // Fill form fields
    fireEvent.change(screen.getByLabelText("Email Address"), {
      target: { value: "caregiver@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "Password123" },
    });
    fireEvent.change(screen.getByLabelText("Confirm Password"), {
      target: { value: "Password123" },
    });

    // Submit form
    fireEvent.click(
      screen.getByRole("button", { name: /create account & accept/i })
    );

    // Wait for API call
    await waitFor(() => {
      expect(mockAcceptCaregiverInvitation).toHaveBeenCalledWith(
        "test-token-123",
        "caregiver@example.com",
        "Password123"
      );
    });

    // Advance timer past the 3-second redirect delay
    jest.advanceTimersByTime(3000);

    expect(mockPush).toHaveBeenCalledWith("/login");
  });
});

describe("Invite Page - Already accepted state", () => {
  it("shows 'Go to Home' link when invitation is already accepted", async () => {
    mockGetInvitationDetails.mockResolvedValue({
      patient_email: "patient@example.com",
      status: "accepted",
      expires_at: new Date(Date.now() + 86400000).toISOString(),
    });

    render(<InviteAcceptPage />);

    await waitFor(() => {
      expect(screen.getByText("Already Accepted")).toBeInTheDocument();
    });

    const homeLink = screen.getByRole("link", { name: "Go to Home" });
    expect(homeLink).toHaveAttribute("href", "/");
  });
});
