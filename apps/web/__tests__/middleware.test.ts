/**
 * Story 15.3: Auth Middleware Tests
 *
 * Tests the middleware logic by mocking NextRequest/NextResponse
 * since Edge runtime APIs aren't available in Jest's node environment.
 */

// Mock next/server before importing middleware
const mockRedirect = jest.fn();
const mockNext = jest.fn();

jest.mock("next/server", () => ({
  NextResponse: {
    redirect: (url: URL) => {
      mockRedirect(url.toString());
      return {
        status: 307,
        headers: new Map([["location", url.toString()]]),
      };
    },
    next: () => {
      mockNext();
      return {
        status: 200,
        headers: new Map(),
      };
    },
  },
}));

import { middleware, config } from "@/middleware";

function createMockRequest(path: string, hasCookie = false) {
  const url = new URL(path, "http://localhost:3000");
  return {
    nextUrl: url,
    url: url.toString(),
    cookies: {
      has: (name: string) => {
        if (name === "glycemicgpt_session") return hasCookie;
        return false;
      },
    },
  } as Parameters<typeof middleware>[0];
}

function getRedirectPath(url: string): string {
  const parsed = new URL(url);
  return parsed.pathname + parsed.search;
}

describe("Auth Middleware", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("protected routes (unauthenticated)", () => {
    it("redirects /dashboard to /login with redirect param", () => {
      const request = createMockRequest("/dashboard");
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe(
        "/login?redirect=%2Fdashboard"
      );
    });

    it("redirects /dashboard/settings to /login with redirect param", () => {
      const request = createMockRequest("/dashboard/settings");
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe(
        "/login?redirect=%2Fdashboard%2Fsettings"
      );
    });

    it("redirects /dashboard/settings/profile to /login with redirect param", () => {
      const request = createMockRequest("/dashboard/settings/profile");
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe(
        "/login?redirect=%2Fdashboard%2Fsettings%2Fprofile"
      );
    });

    it("redirects /dashboard/ai-chat to /login with redirect param", () => {
      const request = createMockRequest("/dashboard/ai-chat");
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe(
        "/login?redirect=%2Fdashboard%2Fai-chat"
      );
    });
  });

  describe("protected routes (authenticated)", () => {
    it("allows /dashboard through with valid cookie", () => {
      const request = createMockRequest("/dashboard", true);
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });

    it("allows /dashboard/settings through with valid cookie", () => {
      const request = createMockRequest("/dashboard/settings", true);
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });

    it("allows /dashboard/alerts through with valid cookie", () => {
      const request = createMockRequest("/dashboard/alerts", true);
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });
  });

  describe("public routes", () => {
    it("allows / without auth (not matched by middleware config)", () => {
      const request = createMockRequest("/");
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });

    it("allows /login without auth", () => {
      const request = createMockRequest("/login");
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });

    it("allows /register without auth", () => {
      const request = createMockRequest("/register");
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });

    it("allows /invite/abc123 without auth (not matched by middleware config)", () => {
      const request = createMockRequest("/invite/abc123");
      middleware(request);

      expect(mockNext).toHaveBeenCalledTimes(1);
      expect(mockRedirect).not.toHaveBeenCalled();
    });
  });

  describe("auth page redirects (authenticated)", () => {
    it("redirects /login to /dashboard when authenticated", () => {
      const request = createMockRequest("/login", true);
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe("/dashboard");
    });

    it("redirects /register to /dashboard when authenticated", () => {
      const request = createMockRequest("/register", true);
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe("/dashboard");
    });
  });

  describe("edge cases", () => {
    it("redirects /dashboard/ (trailing slash) to /login", () => {
      const request = createMockRequest("/dashboard/");
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      expect(getRedirectPath(redirectUrl)).toBe(
        "/login?redirect=%2Fdashboard%2F"
      );
    });

    it("preserves only pathname in redirect param (not query strings)", () => {
      const request = createMockRequest("/dashboard/settings");
      middleware(request);

      expect(mockRedirect).toHaveBeenCalledTimes(1);
      const redirectUrl = mockRedirect.mock.calls[0][0];
      // Middleware uses pathname only, not full URL with query params
      expect(getRedirectPath(redirectUrl)).toBe(
        "/login?redirect=%2Fdashboard%2Fsettings"
      );
    });
  });

  describe("middleware config", () => {
    it("exports matcher config with correct routes", () => {
      expect(config.matcher).toEqual([
        "/dashboard/:path*",
        "/login",
        "/register",
      ]);
    });
  });
});
