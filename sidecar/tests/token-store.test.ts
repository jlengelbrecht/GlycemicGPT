/**
 * Tests for token persistence (token-store.ts).
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { mkdtempSync, rmSync, existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

describe("token-store", () => {
  let tempDir: string;
  let codexDir: string;

  beforeEach(() => {
    tempDir = mkdtempSync(join(tmpdir(), "sidecar-test-"));
    codexDir = join(tempDir, "codex");
    process.env.TOKEN_DIR = tempDir;
    process.env.CODEX_HOME = codexDir;
  });

  afterEach(() => {
    rmSync(tempDir, { recursive: true, force: true });
    delete process.env.TOKEN_DIR;
    delete process.env.CODEX_HOME;
  });

  it("stores and reads Claude token", async () => {
    // Dynamic import to pick up env changes
    const { storeClaudeToken, readClaudeToken } = await import(
      "../src/auth/token-store.js"
    );

    storeClaudeToken("test-token-123");

    const token = readClaudeToken();
    expect(token).toBe("test-token-123");

    // File should exist with restricted permissions
    const filePath = join(tempDir, "claude_token");
    expect(existsSync(filePath)).toBe(true);
    expect(readFileSync(filePath, "utf-8").trim()).toBe("test-token-123");
  });

  it("revokes Claude token", async () => {
    const { storeClaudeToken, readClaudeToken, revokeClaudeToken } =
      await import("../src/auth/token-store.js");

    storeClaudeToken("test-token-123");
    expect(readClaudeToken()).toBe("test-token-123");

    revokeClaudeToken();
    expect(readClaudeToken()).toBeNull();
  });

  it("stores and reads Codex auth", async () => {
    const { storeCodexAuth, readCodexAuth } = await import(
      "../src/auth/token-store.js"
    );

    const auth = { accessToken: "sk-test", expiresAt: 9999999999 };
    storeCodexAuth(auth);

    const stored = readCodexAuth();
    expect(stored).toEqual(auth);
  });

  it("revokes Codex auth", async () => {
    const { storeCodexAuth, readCodexAuth, revokeCodexAuth } = await import(
      "../src/auth/token-store.js"
    );

    storeCodexAuth({ accessToken: "sk-test" });
    expect(readCodexAuth()).toBeTruthy();

    revokeCodexAuth();
    expect(readCodexAuth()).toBeNull();
  });

  it("returns null for missing Claude token", async () => {
    const { readClaudeToken } = await import("../src/auth/token-store.js");
    expect(readClaudeToken()).toBeNull();
  });

  it("returns null for missing Codex auth", async () => {
    const { readCodexAuth } = await import("../src/auth/token-store.js");
    expect(readCodexAuth()).toBeNull();
  });
});
