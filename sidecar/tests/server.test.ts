/**
 * Integration tests for the Express server endpoints.
 *
 * Uses direct function calls against the app (no HTTP server needed).
 */

import { describe, it, expect, vi, beforeAll } from "vitest";
import express from "express";

// Mock singleton providers
const mockClaudeComplete = vi.fn().mockResolvedValue({
  content: "Hello from Claude",
  model: "claude-sonnet",
});
const mockClaudeStream = vi.fn().mockImplementation(
  async (_messages: unknown, _model: unknown, onChunk?: (text: string) => void) => {
    onChunk?.("Hello ");
    onChunk?.("from ");
    onChunk?.("Claude");
    return { content: "Hello from Claude", model: "claude-sonnet" };
  },
);
const mockClaudeCheckAuth = vi.fn().mockResolvedValue({
  authenticated: true,
  provider: "claude",
  message: "Claude OAuth token configured",
});
const mockCodexCheckAuth = vi.fn().mockResolvedValue({
  authenticated: false,
  provider: "codex",
  message: "No Codex authentication found",
});

vi.mock("../src/providers/index.js", () => ({
  claude: {
    checkAuth: mockClaudeCheckAuth,
    complete: mockClaudeComplete,
    stream: mockClaudeStream,
  },
  codex: {
    checkAuth: mockCodexCheckAuth,
    complete: vi.fn(),
    stream: vi.fn(),
  },
}));

// Mock the token store for auth routes
vi.mock("../src/auth/token-store.js", () => ({
  readClaudeToken: vi.fn().mockReturnValue(null),
  readCodexAuth: vi.fn().mockReturnValue(null),
  revokeClaudeToken: vi.fn(),
  revokeCodexAuth: vi.fn(),
}));

// Test helper: invoke Express app without HTTP
async function injectRequest(
  app: express.Express,
  method: "get" | "post",
  path: string,
  body?: unknown,
): Promise<{ status: number; body: unknown; headers: Record<string, string> }> {
  return new Promise((resolve) => {
    const req = {
      method: method.toUpperCase(),
      url: path,
      path,
      ip: "127.0.0.1",
      headers: { "content-type": "application/json" },
      body,
      get: () => undefined,
    } as unknown as express.Request;

    const chunks: string[] = [];
    const headers: Record<string, string> = {};
    let statusCode = 200;

    const res = {
      setHeader: (name: string, value: string) => { headers[name.toLowerCase()] = value; },
      status: (code: number) => { statusCode = code; return res; },
      json: (data: unknown) => {
        resolve({ status: statusCode, body: data, headers });
      },
      write: (data: string) => { chunks.push(data); },
      end: () => {
        resolve({ status: statusCode, body: chunks.join(""), headers });
      },
      sendStatus: (code: number) => {
        resolve({ status: code, body: null, headers });
      },
      flushHeaders: () => {},
    } as unknown as express.Response;

    app(req, res, () => {
      resolve({ status: 404, body: { error: "Not found" }, headers });
    });
  });
}

describe("Server endpoints", () => {
  let app: express.Express;

  beforeAll(async () => {
    const mod = await import("../src/server.js");
    app = mod.app;
  });

  describe("GET /health", () => {
    it("returns health response", async () => {
      const result = await injectRequest(app, "get", "/health");
      expect(result.status).toBe(200);
      const body = result.body as Record<string, unknown>;
      expect(body).toHaveProperty("status");
      expect(body).toHaveProperty("claude_auth");
      expect(body).toHaveProperty("codex_auth");
    });
  });

  describe("GET /v1/models", () => {
    it("returns model list for authenticated providers", async () => {
      const result = await injectRequest(app, "get", "/v1/models");
      expect(result.status).toBe(200);
      const body = result.body as { data: Array<{ id: string }> };
      expect(body.data).toBeDefined();
      expect(body.data.some((m) => m.id.includes("claude"))).toBe(true);
    });
  });

  describe("POST /v1/chat/completions", () => {
    it("rejects request without messages", async () => {
      const result = await injectRequest(app, "post", "/v1/chat/completions", {});
      expect(result.status).toBe(400);
      const body = result.body as { error: { message: string } };
      expect(body.error.message).toContain("messages is required");
    });

    it("rejects empty messages array", async () => {
      const result = await injectRequest(app, "post", "/v1/chat/completions", {
        messages: [],
      });
      expect(result.status).toBe(400);
    });

    it("rejects messages with invalid role", async () => {
      const result = await injectRequest(app, "post", "/v1/chat/completions", {
        messages: [{ role: "admin", content: "test" }],
      });
      expect(result.status).toBe(400);
      const body = result.body as { error: { message: string } };
      expect(body.error.message).toContain("role must be");
    });

    it("rejects messages with non-string content", async () => {
      const result = await injectRequest(app, "post", "/v1/chat/completions", {
        messages: [{ role: "user", content: 123 }],
      });
      expect(result.status).toBe(400);
      const body = result.body as { error: { message: string } };
      expect(body.error.message).toContain("content must be a string");
    });

    it("returns non-streaming completion", async () => {
      const result = await injectRequest(app, "post", "/v1/chat/completions", {
        messages: [{ role: "user", content: "Hello" }],
        stream: false,
      });
      expect(result.status).toBe(200);
      const body = result.body as {
        id: string;
        choices: Array<{ message: { content: string } }>;
      };
      expect(body.id).toMatch(/^chatcmpl-/);
      expect(body.choices[0].message.content).toBe("Hello from Claude");
    });

    it("returns streaming completion as SSE", async () => {
      const result = await injectRequest(app, "post", "/v1/chat/completions", {
        messages: [{ role: "user", content: "Hello" }],
        stream: true,
      });
      expect(result.headers["content-type"]).toBe("text/event-stream");
      const sseData = result.body as string;
      expect(sseData).toContain("data:");
      expect(sseData).toContain("[DONE]");
    });
  });

  describe("GET /auth/status", () => {
    it("returns auth status for both providers", async () => {
      const result = await injectRequest(app, "get", "/auth/status");
      expect(result.status).toBe(200);
      const body = result.body as {
        claude: { authenticated: boolean };
        codex: { authenticated: boolean };
      };
      expect(body).toHaveProperty("claude");
      expect(body).toHaveProperty("codex");
    });
  });

  describe("POST /auth/start", () => {
    it("returns 501 for unimplemented OAuth flow", async () => {
      const result = await injectRequest(app, "post", "/auth/start", {
        provider: "claude",
      });
      expect(result.status).toBe(501);
    });

    it("rejects invalid provider", async () => {
      const result = await injectRequest(app, "post", "/auth/start", {
        provider: "invalid",
      });
      expect(result.status).toBe(400);
    });
  });

  describe("Security headers", () => {
    it("sets security headers on responses", async () => {
      const result = await injectRequest(app, "get", "/health");
      expect(result.headers["x-content-type-options"]).toBe("nosniff");
      expect(result.headers["x-frame-options"]).toBe("DENY");
    });
  });
});
