/**
 * Story 15.2: Registration Page Tests
 */
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockReplace = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

// Mock next/image
jest.mock("next/image", () => ({
  __esModule: true,
  default: (props: Record<string, unknown>) => {
    const { priority, ...rest } = props;
    return <img {...rest} data-priority={priority ? "true" : undefined} />;
  },
}));

// Mock API functions
const mockRegisterUser = jest.fn();
const mockLoginUser = jest.fn();
const mockGetCurrentUser = jest.fn();
jest.mock("@/lib/api", () => ({
  registerUser: (...args: unknown[]) => mockRegisterUser(...args),
  loginUser: (...args: unknown[]) => mockLoginUser(...args),
  getCurrentUser: (...args: unknown[]) => mockGetCurrentUser(...args),
}));

import RegisterPage from "@/app/register/page";

describe("Registration Page", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Default: not authenticated
    mockGetCurrentUser.mockRejectedValue(new Error("Not authenticated"));
  });

  it("renders email, password, and confirm password fields", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^confirm password$/i)).toBeInTheDocument();
  });

  it("renders Create Account heading and button", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /create account/i })
      ).toBeInTheDocument();
    });
    expect(
      screen.getByRole("button", { name: /create account/i })
    ).toBeInTheDocument();
  });

  it("renders Sign in link to /login", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: /sign in/i })
      ).toHaveAttribute("href", "/login");
    });
  });

  it("renders Back to home link", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(
        screen.getByRole("link", { name: /back to home/i })
      ).toHaveAttribute("href", "/");
    });
  });

  describe("password strength indicators", () => {
    it("shows requirements when password field is focused", async () => {
      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
      });

      // Requirements should NOT be visible initially
      expect(screen.queryByText(/at least 8 characters/i)).not.toBeInTheDocument();

      // Focus the password field
      fireEvent.focus(screen.getByLabelText(/^password$/i));

      // Requirements should now be visible
      expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
      expect(screen.getByText(/one uppercase letter/i)).toBeInTheDocument();
      expect(screen.getByText(/one lowercase letter/i)).toBeInTheDocument();
      expect(screen.getByText(/one number/i)).toBeInTheDocument();
    });

    it("shows requirements when password has content", async () => {
      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
      });

      await userEvent.type(screen.getByLabelText(/^password$/i), "a");

      expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
    });

    it("marks met requirements with green styling", async () => {
      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
      });

      await userEvent.type(screen.getByLabelText(/^password$/i), "Abcdefg1");

      // All requirements met
      const items = screen.getByRole("list", { name: /password requirements/i });
      const listItems = items.querySelectorAll("li");
      listItems.forEach((li) => {
        expect(li.className).toContain("text-green-400");
      });
    });

    it("marks unmet requirements with slate styling", async () => {
      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
      });

      // Type only lowercase - missing uppercase, number, and length
      await userEvent.type(screen.getByLabelText(/^password$/i), "abc");

      const items = screen.getByRole("list", { name: /password requirements/i });
      const listItems = items.querySelectorAll("li");

      // "One lowercase letter" should be green
      const lowercaseItem = Array.from(listItems).find((li) =>
        li.textContent?.includes("One lowercase letter")
      );
      expect(lowercaseItem?.className).toContain("text-green-400");

      // "At least 8 characters" should be slate
      const lengthItem = Array.from(listItems).find((li) =>
        li.textContent?.includes("At least 8 characters")
      );
      expect(lengthItem?.className).toContain("text-slate-500");
    });
  });

  describe("form validation", () => {
    it("shows error when passwords do not match", async () => {
      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "test@test.com"
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "DifferentPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(screen.getByRole("alert")).toHaveTextContent(
          "Passwords do not match"
        );
      });

      // Should NOT call API
      expect(mockRegisterUser).not.toHaveBeenCalled();
    });

    it("shows error when password does not meet requirements", async () => {
      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "test@test.com"
      );
      await userEvent.type(screen.getByLabelText(/^password$/i), "weak");
      await userEvent.type(screen.getByLabelText(/^confirm password$/i), "weak");

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(screen.getByRole("alert")).toHaveTextContent(
          "Password does not meet requirements"
        );
      });

      // Should NOT call API
      expect(mockRegisterUser).not.toHaveBeenCalled();
    });
  });

  describe("form submission", () => {
    it("calls registerUser then loginUser on valid submit", async () => {
      mockRegisterUser.mockResolvedValue({
        id: "1",
        email: "new@test.com",
        role: "diabetic",
        message: "Registration successful",
        disclaimer_required: true,
      });
      mockLoginUser.mockResolvedValue({
        message: "Login successful",
        user: { id: "1", email: "new@test.com" },
        disclaimer_required: true,
      });

      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "new@test.com"
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "TestPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(mockRegisterUser).toHaveBeenCalledWith(
          "new@test.com",
          "TestPass123"
        );
      });

      await waitFor(() => {
        expect(mockLoginUser).toHaveBeenCalledWith(
          "new@test.com",
          "TestPass123"
        );
      });
    });

    it("redirects to dashboard after successful registration", async () => {
      mockRegisterUser.mockResolvedValue({
        id: "1",
        email: "new@test.com",
        role: "diabetic",
        message: "Registration successful",
        disclaimer_required: true,
      });
      mockLoginUser.mockResolvedValue({
        message: "Login successful",
        user: { id: "1", email: "new@test.com" },
        disclaimer_required: true,
      });

      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "new@test.com"
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "TestPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(mockReplace).toHaveBeenCalledWith("/dashboard");
      });
    });

    it("displays duplicate email error from API", async () => {
      mockRegisterUser.mockRejectedValue(
        new Error("An account with this email already exists")
      );

      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "existing@test.com"
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "TestPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(screen.getByRole("alert")).toHaveTextContent(
          "An account with this email already exists"
        );
      });
    });

    it("displays fallback error for non-Error rejections", async () => {
      mockRegisterUser.mockRejectedValue("unknown error");

      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "new@test.com"
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "TestPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(screen.getByRole("alert")).toHaveTextContent(
          "An unexpected error occurred"
        );
      });
    });

    it("shows loading state during submission", async () => {
      // Never resolve to keep the loading state
      mockRegisterUser.mockReturnValue(new Promise(() => {}));

      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "new@test.com"
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "TestPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(screen.getByText(/creating account/i)).toBeInTheDocument();
      });

      expect(
        screen.getByRole("button", { name: /creating account/i })
      ).toBeDisabled();
    });

    it("trims whitespace from email before submission", async () => {
      mockRegisterUser.mockResolvedValue({
        id: "1",
        email: "new@test.com",
        role: "diabetic",
        message: "Registration successful",
        disclaimer_required: true,
      });
      mockLoginUser.mockResolvedValue({
        message: "Login successful",
        user: { id: "1", email: "new@test.com" },
        disclaimer_required: true,
      });

      render(<RegisterPage />);
      await waitFor(() => {
        expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      });

      await userEvent.type(
        screen.getByLabelText(/email address/i),
        "  new@test.com  "
      );
      await userEvent.type(
        screen.getByLabelText(/^password$/i),
        "TestPass123"
      );
      await userEvent.type(
        screen.getByLabelText(/^confirm password$/i),
        "TestPass123"
      );

      fireEvent.click(
        screen.getByRole("button", { name: /create account/i })
      );

      await waitFor(() => {
        expect(mockRegisterUser).toHaveBeenCalledWith(
          "new@test.com",
          "TestPass123"
        );
      });
    });
  });

  it("redirects authenticated users to dashboard", async () => {
    mockGetCurrentUser.mockResolvedValue({
      id: "1",
      email: "test@test.com",
    });

    render(<RegisterPage />);

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("toggles password visibility", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    });

    const passwordInput = screen.getByLabelText(/^password$/i);
    expect(passwordInput).toHaveAttribute("type", "password");

    const toggleButton = screen.getByRole("button", {
      name: /^show password$/i,
    });
    fireEvent.click(toggleButton);

    expect(passwordInput).toHaveAttribute("type", "text");

    const hideButton = screen.getByRole("button", {
      name: /^hide password$/i,
    });
    fireEvent.click(hideButton);

    expect(passwordInput).toHaveAttribute("type", "password");
  });

  it("toggles confirm password visibility", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/^confirm password$/i)).toBeInTheDocument();
    });

    const confirmInput = screen.getByLabelText(/^confirm password$/i);
    expect(confirmInput).toHaveAttribute("type", "password");

    const toggleButton = screen.getByRole("button", {
      name: /show confirm password/i,
    });
    fireEvent.click(toggleButton);

    expect(confirmInput).toHaveAttribute("type", "text");
  });

  it("redirects to login when auto-login fails after successful registration", async () => {
    mockRegisterUser.mockResolvedValue({
      id: "1",
      email: "new@test.com",
      role: "diabetic",
      message: "Registration successful",
      disclaimer_required: true,
    });
    mockLoginUser.mockRejectedValue(new Error("Login service unavailable"));

    render(<RegisterPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });

    await userEvent.type(
      screen.getByLabelText(/email address/i),
      "new@test.com"
    );
    await userEvent.type(
      screen.getByLabelText(/^password$/i),
      "TestPass123"
    );
    await userEvent.type(
      screen.getByLabelText(/^confirm password$/i),
      "TestPass123"
    );

    fireEvent.click(
      screen.getByRole("button", { name: /create account/i })
    );

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/login");
    });
  });

  it("keeps button enabled after client-side validation error", async () => {
    render(<RegisterPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });

    await userEvent.type(
      screen.getByLabelText(/email address/i),
      "test@test.com"
    );
    await userEvent.type(screen.getByLabelText(/^password$/i), "weak");
    await userEvent.type(screen.getByLabelText(/^confirm password$/i), "weak");

    fireEvent.click(
      screen.getByRole("button", { name: /create account/i })
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    // Button should still show "Create Account", not loading state
    expect(
      screen.getByRole("button", { name: /create account/i })
    ).not.toBeDisabled();
  });

  it("clears previous error on resubmit", async () => {
    mockRegisterUser.mockRejectedValueOnce(
      new Error("An account with this email already exists")
    );
    mockRegisterUser.mockResolvedValueOnce({
      id: "1",
      email: "new@test.com",
      role: "diabetic",
      message: "Registration successful",
      disclaimer_required: true,
    });
    mockLoginUser.mockResolvedValue({
      message: "Login successful",
      user: { id: "1", email: "new@test.com" },
      disclaimer_required: true,
    });

    render(<RegisterPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
    });

    await userEvent.type(
      screen.getByLabelText(/email address/i),
      "new@test.com"
    );
    await userEvent.type(
      screen.getByLabelText(/^password$/i),
      "TestPass123"
    );
    await userEvent.type(
      screen.getByLabelText(/^confirm password$/i),
      "TestPass123"
    );

    // First submit: should show error
    fireEvent.click(
      screen.getByRole("button", { name: /create account/i })
    );

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "An account with this email already exists"
      );
    });

    // Second submit: error should clear
    fireEvent.click(
      screen.getByRole("button", { name: /create account/i })
    );

    // Error should be cleared during resubmission
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/dashboard");
    });
  });
});
