/**
 * Tests for the main page component.
 * Story 1.1 AC: Web UI accessible at localhost:3000
 * Story 15.6: Landing page auth navigation polish
 */

import { render, screen, waitFor } from "@testing-library/react";

// Mock next/image
jest.mock("next/image", () => {
  return function MockImage({
    alt,
    ...props
  }: {
    alt: string;
    [key: string]: unknown;
  }) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img alt={alt} {...props} />;
  };
});

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

// Mock DisclaimerModal
jest.mock("@/components/disclaimer-modal", () => ({
  DisclaimerModal: () => <div data-testid="disclaimer-modal" />,
}));

// Mock getCurrentUser
const mockGetCurrentUser = jest.fn();
jest.mock("@/lib/api", () => ({
  getCurrentUser: () => mockGetCurrentUser(),
}));

import Home from "../src/app/page";

beforeEach(() => {
  jest.clearAllMocks();
});

describe("Home Page", () => {
  it("renders the GlycemicGPT heading", () => {
    mockGetCurrentUser.mockRejectedValue(new Error("Not authenticated"));
    render(<Home />);
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("GlycemicGPT");
  });

  it("renders the tagline", () => {
    mockGetCurrentUser.mockRejectedValue(new Error("Not authenticated"));
    render(<Home />);
    expect(screen.getByText("Your on-call endo at home")).toBeInTheDocument();
  });
});

describe("Home Page - Unauthenticated", () => {
  beforeEach(() => {
    mockGetCurrentUser.mockRejectedValue(new Error("Not authenticated"));
  });

  it("shows Sign In and Create Account buttons when not authenticated", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Sign In" })).toBeInTheDocument();
    });

    expect(
      screen.getByRole("link", { name: "Create Account" })
    ).toBeInTheDocument();
  });

  it("Sign In links to /login", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Sign In" })).toHaveAttribute(
        "href",
        "/login"
      );
    });
  });

  it("Create Account links to /register", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: "Create Account" })
      ).toHaveAttribute("href", "/register");
    });
  });

  it("does not show Go to Dashboard when unauthenticated", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Sign In" })).toBeInTheDocument();
    });

    expect(
      screen.queryByRole("link", { name: "Go to Dashboard" })
    ).not.toBeInTheDocument();
  });
});

describe("Home Page - Authenticated", () => {
  beforeEach(() => {
    mockGetCurrentUser.mockResolvedValue({
      id: "1",
      email: "test@example.com",
      disclaimer_acknowledged: true,
    });
  });

  it("shows Go to Dashboard when authenticated", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: "Go to Dashboard" })
      ).toBeInTheDocument();
    });
  });

  it("Go to Dashboard links to /dashboard", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: "Go to Dashboard" })
      ).toHaveAttribute("href", "/dashboard");
    });
  });

  it("does not show Sign In or Create Account when authenticated", async () => {
    render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: "Go to Dashboard" })
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByRole("link", { name: "Sign In" })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Create Account" })
    ).not.toBeInTheDocument();
  });
});

describe("Home Page - Initial State", () => {
  it("shows Sign In and Create Account by default while checking auth", () => {
    // Make getCurrentUser hang (never resolve)
    mockGetCurrentUser.mockReturnValue(new Promise(() => {}));

    render(<Home />);

    // While auth check is in progress (isAuthenticated === null), show sign in/register
    expect(screen.getByRole("link", { name: "Sign In" })).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Create Account" })
    ).toBeInTheDocument();
  });
});
