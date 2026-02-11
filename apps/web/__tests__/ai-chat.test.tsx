/**
 * Story 11.2: Web-Based AI Chat Interface
 *
 * Tests for the AI Chat page including all states (loading, no-provider,
 * offline, ready), message sending, error handling, and UI interactions.
 */

import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";

// Mock next/navigation
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), back: jest.fn() }),
  usePathname: () => "/dashboard/ai-chat",
}));

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

// Mock API functions
const mockGetAIProvider = jest.fn();
const mockSendAIChat = jest.fn();

jest.mock("@/lib/api", () => ({
  getAIProvider: (...args: unknown[]) => mockGetAIProvider(...args),
  sendAIChat: (...args: unknown[]) => mockSendAIChat(...args),
}));

import AIChatPage from "@/app/dashboard/ai-chat/page";

// jsdom doesn't implement scrollIntoView
Element.prototype.scrollIntoView = jest.fn();

beforeEach(() => {
  jest.clearAllMocks();
});

describe("AI Chat Page", () => {
  describe("loading state", () => {
    it("shows loading spinner while checking provider", () => {
      mockGetAIProvider.mockReturnValue(new Promise(() => {}));
      render(<AIChatPage />);
      expect(screen.getByText("Checking AI provider...")).toBeInTheDocument();
    });
  });

  describe("no provider configured", () => {
    beforeEach(() => {
      mockGetAIProvider.mockRejectedValue(
        new Error("No AI provider configured")
      );
    });

    it("shows configure prompt when no provider", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(screen.getByText("AI Chat")).toBeInTheDocument();
      });

      expect(
        screen.getByText(/configure an AI provider first/)
      ).toBeInTheDocument();
    });

    it("links to AI provider settings", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(screen.getByText("Configure AI Provider")).toBeInTheDocument();
      });

      const link = screen.getByText("Configure AI Provider").closest("a");
      expect(link).toHaveAttribute(
        "href",
        "/dashboard/settings/ai-provider"
      );
    });
  });

  describe("offline state", () => {
    beforeEach(() => {
      mockGetAIProvider.mockRejectedValue(new Error("Failed to fetch"));
    });

    it("shows offline message when server unreachable", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(screen.getByText("Unable to Connect")).toBeInTheDocument();
      });

      expect(
        screen.getByText(/Cannot reach the server/)
      ).toBeInTheDocument();
    });

    it("shows retry button", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByRole("button", { name: /retry connection/i })
        ).toBeInTheDocument();
      });
    });

    it("retries and transitions to ready on success", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByRole("button", { name: /retry connection/i })
        ).toBeInTheDocument();
      });

      // Now getAIProvider succeeds on retry
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /retry connection/i })
        );
      });

      await waitFor(() => {
        expect(
          screen.getByText("Start a conversation")
        ).toBeInTheDocument();
      });
    });
  });

  describe("ready state - empty chat", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
    });

    it("shows chat header", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(screen.getByText("AI Chat")).toBeInTheDocument();
      });

      expect(
        screen.getByText("Ask questions about your glucose data")
      ).toBeInTheDocument();
    });

    it("shows empty state with suggestions", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByText("Start a conversation")
        ).toBeInTheDocument();
      });

      expect(
        screen.getByText("How am I doing today?")
      ).toBeInTheDocument();
      expect(
        screen.getByText("Why do I spike after breakfast?")
      ).toBeInTheDocument();
    });

    it("fills input when clicking a suggestion", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByText("How am I doing today?")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.click(screen.getByText("How am I doing today?"));
      });

      const textarea = screen.getByPlaceholderText(
        "Ask about your glucose data..."
      );
      expect(textarea).toHaveValue("How am I doing today?");
    });

    it("shows disclaimer text", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByText(
            "Not medical advice. Consult your healthcare provider."
          )
        ).toBeInTheDocument();
      });
    });

    it("shows send button disabled when input is empty", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByRole("button", { name: /send message/i })
        ).toBeDisabled();
      });
    });

    it("enables send button when input has text", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Hello" } }
        );
      });

      expect(
        screen.getByRole("button", { name: /send message/i })
      ).not.toBeDisabled();
    });

    it("does not show clear button when no messages", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByText("Start a conversation")
        ).toBeInTheDocument();
      });

      expect(
        screen.queryByRole("button", { name: /clear chat history/i })
      ).not.toBeInTheDocument();
    });
  });

  describe("sending messages", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
    });

    it("sends message and shows response", async () => {
      mockSendAIChat.mockResolvedValue({
        response: "Your glucose looks stable today.",
        disclaimer: "Not medical advice. Consult your healthcare provider.",
      });

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      const textarea = screen.getByPlaceholderText(
        "Ask about your glucose data..."
      );

      await act(async () => {
        fireEvent.change(textarea, {
          target: { value: "How am I doing?" },
        });
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      // User message should appear
      await waitFor(() => {
        expect(screen.getByText("How am I doing?")).toBeInTheDocument();
      });

      // AI response should appear
      await waitFor(() => {
        expect(
          screen.getByText("Your glucose looks stable today.")
        ).toBeInTheDocument();
      });

      expect(mockSendAIChat).toHaveBeenCalledWith("How am I doing?");
    });

    it("shows typing indicator while waiting", async () => {
      let resolveChat: (value: unknown) => void;
      mockSendAIChat.mockReturnValue(
        new Promise((resolve) => {
          resolveChat = resolve;
        })
      );

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Test message" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      // Typing indicator should show
      expect(screen.getByText("AI is thinking...")).toBeInTheDocument();

      // Resolve the promise
      await act(async () => {
        resolveChat!({
          response: "Response",
          disclaimer: "Disclaimer",
        });
      });

      // Typing indicator should be gone
      expect(
        screen.queryByText("AI is thinking...")
      ).not.toBeInTheDocument();
    });

    it("clears input after sending", async () => {
      mockSendAIChat.mockResolvedValue({
        response: "Reply",
        disclaimer: "Disclaimer",
      });

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      const textarea = screen.getByPlaceholderText(
        "Ask about your glucose data..."
      );

      await act(async () => {
        fireEvent.change(textarea, {
          target: { value: "My question" },
        });
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      // Input should be cleared immediately
      expect(textarea).toHaveValue("");
    });

    it("sends on Enter key (without shift)", async () => {
      mockSendAIChat.mockResolvedValue({
        response: "Reply",
        disclaimer: "Disclaimer",
      });

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      const textarea = screen.getByPlaceholderText(
        "Ask about your glucose data..."
      );

      await act(async () => {
        fireEvent.change(textarea, {
          target: { value: "Enter test" },
        });
      });

      await act(async () => {
        fireEvent.keyDown(textarea, { key: "Enter", shiftKey: false });
      });

      expect(mockSendAIChat).toHaveBeenCalledWith("Enter test");
    });

    it("does NOT send on Shift+Enter", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      const textarea = screen.getByPlaceholderText(
        "Ask about your glucose data..."
      );

      await act(async () => {
        fireEvent.change(textarea, {
          target: { value: "Multi-line" },
        });
      });

      await act(async () => {
        fireEvent.keyDown(textarea, { key: "Enter", shiftKey: true });
      });

      expect(mockSendAIChat).not.toHaveBeenCalled();
    });

    it("shows error on send failure", async () => {
      mockSendAIChat.mockRejectedValue(
        new Error("Unable to get a response from the AI provider")
      );

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Failing message" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(
          screen.getByText(
            "Unable to get a response from the AI provider"
          )
        ).toBeInTheDocument();
      });
    });

    it("shows disclaimer on AI response", async () => {
      mockSendAIChat.mockResolvedValue({
        response: "Your readings look good.",
        disclaimer: "Not medical advice. Consult your healthcare provider.",
      });

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "How are my readings?" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      // After sending, the disclaimer should appear on the AI response
      // (in addition to the static disclaimer bar already present)
      await waitFor(() => {
        const disclaimers = screen.getAllByText(
          "Not medical advice. Consult your healthcare provider."
        );
        // One from the static bar, one from the AI response bubble
        expect(disclaimers.length).toBeGreaterThanOrEqual(2);
      });
    });
  });

  describe("clear chat", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
      mockSendAIChat.mockResolvedValue({
        response: "Response text",
        disclaimer: "Disclaimer",
      });
    });

    it("shows clear button after messages exist", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      // Send a message first
      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Test" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(
          screen.getByRole("button", { name: /clear chat history/i })
        ).toBeInTheDocument();
      });
    });

    it("clears all messages when clear is clicked", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      // Send a message
      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Test" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(screen.getByText("Response text")).toBeInTheDocument();
      });

      // Clear chat
      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /clear chat history/i })
        );
      });

      // Messages should be gone, empty state should return
      expect(screen.queryByText("Test")).not.toBeInTheDocument();
      expect(screen.queryByText("Response text")).not.toBeInTheDocument();
      expect(
        screen.getByText("Start a conversation")
      ).toBeInTheDocument();
    });
  });

  describe("multiple messages", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
    });

    it("supports multiple exchanges in sequence", async () => {
      mockSendAIChat
        .mockResolvedValueOnce({
          response: "First reply",
          disclaimer: "Disclaimer",
        })
        .mockResolvedValueOnce({
          response: "Second reply",
          disclaimer: "Disclaimer",
        });

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      // Send first message
      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "First question" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(screen.getByText("First reply")).toBeInTheDocument();
      });

      // Send second message
      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Second question" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(screen.getByText("Second reply")).toBeInTheDocument();
      });

      // Both exchanges should be visible
      expect(screen.getByText("First question")).toBeInTheDocument();
      expect(screen.getByText("First reply")).toBeInTheDocument();
      expect(screen.getByText("Second question")).toBeInTheDocument();
      expect(screen.getByText("Second reply")).toBeInTheDocument();
    });
  });

  describe("does not send empty messages", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
    });

    it("does not send whitespace-only input", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "   " } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      expect(mockSendAIChat).not.toHaveBeenCalled();
    });
  });

  describe("accessibility", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
    });

    it("has role=log on messages area", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(screen.getByRole("log")).toBeInTheDocument();
      });
    });

    it("has aria-label on message input", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByLabelText("Message input")
        ).toBeInTheDocument();
      });
    });

    it("has maxLength on textarea", async () => {
      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toHaveAttribute("maxLength", "2000");
      });
    });

    it("shows typing indicator with role=status", async () => {
      mockSendAIChat.mockReturnValue(new Promise(() => {}));

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Test" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      expect(screen.getByRole("status")).toBeInTheDocument();
    });
  });

  describe("error recovery", () => {
    beforeEach(() => {
      mockGetAIProvider.mockResolvedValue({
        provider_type: "claude",
        status: "connected",
      });
    });

    it("clears error on next successful send", async () => {
      // First send fails
      mockSendAIChat.mockRejectedValueOnce(new Error("AI provider error"));

      render(<AIChatPage />);

      await waitFor(() => {
        expect(
          screen.getByPlaceholderText("Ask about your glucose data...")
        ).toBeInTheDocument();
      });

      // Send failing message
      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Failing message" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(screen.getByText("AI provider error")).toBeInTheDocument();
      });

      // Second send succeeds
      mockSendAIChat.mockResolvedValueOnce({
        response: "Success!",
        disclaimer: "Disclaimer",
      });

      await act(async () => {
        fireEvent.change(
          screen.getByPlaceholderText("Ask about your glucose data..."),
          { target: { value: "Working message" } }
        );
      });

      await act(async () => {
        fireEvent.click(
          screen.getByRole("button", { name: /send message/i })
        );
      });

      await waitFor(() => {
        expect(screen.getByText("Success!")).toBeInTheDocument();
      });

      // Error should be cleared
      expect(
        screen.queryByText("AI provider error")
      ).not.toBeInTheDocument();
    });
  });
});
