/**
 * Claude Code CLI subprocess wrapper.
 *
 * Spawns `claude` CLI in non-interactive mode (`--print --output-format stream-json`)
 * and translates between OpenAI chat format and CLI stdin/stdout.
 *
 * Authentication: reads CLAUDE_CODE_OAUTH_TOKEN from environment or
 * a persisted token file at TOKEN_DIR/claude_token.
 */

import { spawn, type ChildProcess } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type {
  AIProvider,
  ChatMessage,
  ProviderAuthState,
  ProviderResult,
} from "./types.js";

const TOKEN_DIR = process.env.TOKEN_DIR || "/home/sidecar/.config/sidecar";
const CLAUDE_TOKEN_FILE = join(TOKEN_DIR, "claude_token");

/** Subprocess timeout (2 minutes) */
const SUBPROCESS_TIMEOUT_MS = 120_000;
/** Maximum stdout/stderr buffer size (10 MB) */
const MAX_BUFFER_BYTES = 10 * 1024 * 1024;
/** Maximum prompt length (100 KB) */
const MAX_PROMPT_LENGTH = 100_000;

/** Strict allowlist of model names to Claude CLI aliases */
const MODEL_MAP: Record<string, string> = {
  "claude-opus-4": "opus",
  "claude-sonnet-4": "sonnet",
  "claude-haiku-4": "haiku",
  "claude-opus-4-6": "opus",
  "claude-sonnet-4-5-20250929": "sonnet",
  "claude-haiku-4-5-20251001": "haiku",
  // Allow bare aliases
  opus: "opus",
  sonnet: "sonnet",
  haiku: "haiku",
};

/** Resolve model name to CLI alias. Rejects unknown models. */
export function resolveModel(model?: string): string {
  if (!model) return "sonnet";
  const resolved = MODEL_MAP[model];
  if (!resolved) {
    throw new Error(`Unsupported model: ${model}`);
  }
  return resolved;
}

/** Read the stored OAuth token, preferring env var over file */
function getToken(): string | null {
  const envToken = process.env.CLAUDE_CODE_OAUTH_TOKEN;
  if (envToken) return envToken;

  try {
    if (existsSync(CLAUDE_TOKEN_FILE)) {
      return readFileSync(CLAUDE_TOKEN_FILE, "utf-8").trim();
    }
  } catch {
    // File unreadable — treat as unauthenticated
  }
  return null;
}

/** Flatten messages into a single prompt string for the CLI */
function messagesToPrompt(messages: ChatMessage[]): string {
  const prompt = messages
    .map((m) => {
      if (m.role === "system") return `[System]: ${m.content}`;
      if (m.role === "user") return m.content;
      return `[Assistant]: ${m.content}`;
    })
    .join("\n\n");

  if (prompt.length > MAX_PROMPT_LENGTH) {
    throw new Error(
      `Prompt too long (${prompt.length} chars, max ${MAX_PROMPT_LENGTH})`,
    );
  }
  return prompt;
}

/**
 * Spawn the Claude CLI as a child process.
 * Prompt is passed via stdin (not as a CLI argument) to prevent injection.
 */
function spawnClaude(prompt: string, model: string): ChildProcess {
  const token = getToken();
  const env: Record<string, string> = { ...process.env } as Record<
    string,
    string
  >;
  if (token) env.CLAUDE_CODE_OAUTH_TOKEN = token;

  const child = spawn(
    "claude",
    [
      "--print",
      "--output-format",
      "stream-json",
      "--model",
      model, // Already validated by resolveModel()
      "-", // Read prompt from stdin
    ],
    {
      env,
      stdio: ["pipe", "pipe", "pipe"],
    },
  );

  // Write prompt to stdin and close
  child.stdin?.write(prompt);
  child.stdin?.end();

  return child;
}

/**
 * Parse a single line of Claude CLI stream-json output and return the
 * text delta (if any).
 */
function extractTextDelta(line: string): string | null {
  try {
    const obj = JSON.parse(line);
    if (obj?.type === "content_block_delta" && obj?.delta?.text) {
      return obj.delta.text as string;
    }
    if (obj?.type === "assistant" && typeof obj?.message?.content === "string") {
      return obj.message.content as string;
    }
    if (obj?.type === "stream_event") {
      const inner = obj.event;
      if (inner?.type === "content_block_delta" && inner?.delta?.text) {
        return inner.delta.text as string;
      }
    }
  } catch {
    // Not valid JSON — skip
  }
  return null;
}

/** Kill a child process and clear its timeout */
function cleanupChild(child: ChildProcess, timer: ReturnType<typeof setTimeout>): void {
  clearTimeout(timer);
  if (!child.killed) child.kill();
}

export class ClaudeProvider implements AIProvider {
  async checkAuth(): Promise<ProviderAuthState> {
    const token = getToken();
    return {
      authenticated: !!token,
      provider: "claude",
      message: token
        ? "Claude OAuth token configured"
        : "No Claude OAuth token found",
    };
  }

  async complete(
    messages: ChatMessage[],
    model?: string,
  ): Promise<ProviderResult> {
    const prompt = messagesToPrompt(messages);
    const cliModel = resolveModel(model);

    return new Promise((resolve, reject) => {
      const child = spawnClaude(prompt, cliModel);
      let stdout = "";
      let stdoutSize = 0;
      let stderrSize = 0;

      const timer = setTimeout(() => {
        child.kill();
        reject(new Error("AI provider request timed out"));
      }, SUBPROCESS_TIMEOUT_MS);

      child.stdout?.on("data", (chunk: Buffer) => {
        stdoutSize += chunk.length;
        if (stdoutSize > MAX_BUFFER_BYTES) {
          cleanupChild(child, timer);
          reject(new Error("AI provider response too large"));
          return;
        }
        stdout += chunk.toString();
      });
      child.stderr?.on("data", (chunk: Buffer) => {
        stderrSize += chunk.length;
        if (stderrSize > MAX_BUFFER_BYTES) {
          cleanupChild(child, timer);
          reject(new Error("AI provider error output too large"));
        }
      });

      child.on("error", (err) => {
        clearTimeout(timer);
        reject(new Error(`Claude CLI failed to start: ${err.message}`));
      });

      child.on("close", (code) => {
        clearTimeout(timer);
        if (code !== 0) {
          reject(new Error("AI provider returned an error"));
          return;
        }

        const lines = stdout.split("\n").filter((l) => l.trim());
        let content = "";
        for (const line of lines) {
          const delta = extractTextDelta(line);
          if (delta) content += delta;
        }

        resolve({ content, model: `claude-${cliModel}` });
      });
    });
  }

  async stream(
    messages: ChatMessage[],
    model?: string,
    onChunk?: (text: string) => void,
  ): Promise<ProviderResult> {
    const prompt = messagesToPrompt(messages);
    const cliModel = resolveModel(model);

    return new Promise((resolve, reject) => {
      const child = spawnClaude(prompt, cliModel);
      let buffer = "";
      let content = "";
      let totalSize = 0;

      const timer = setTimeout(() => {
        child.kill();
        reject(new Error("AI provider request timed out"));
      }, SUBPROCESS_TIMEOUT_MS);

      child.stdout?.on("data", (chunk: Buffer) => {
        totalSize += chunk.length;
        if (totalSize > MAX_BUFFER_BYTES) {
          cleanupChild(child, timer);
          reject(new Error("AI provider response too large"));
          return;
        }
        buffer += chunk.toString();
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";

        for (const line of lines) {
          if (!line.trim()) continue;
          const delta = extractTextDelta(line);
          if (delta) {
            content += delta;
            onChunk?.(delta);
          }
        }
      });

      let stderrSize = 0;
      child.stderr?.on("data", (chunk: Buffer) => {
        stderrSize += chunk.length;
        if (stderrSize > MAX_BUFFER_BYTES) {
          cleanupChild(child, timer);
          reject(new Error("AI provider error output too large"));
        }
      });

      child.on("error", (err) => {
        clearTimeout(timer);
        reject(new Error(`Claude CLI failed to start: ${err.message}`));
      });

      child.on("close", (code) => {
        clearTimeout(timer);
        if (buffer.trim()) {
          const delta = extractTextDelta(buffer);
          if (delta) {
            content += delta;
            onChunk?.(delta);
          }
        }

        if (code !== 0 && !content) {
          reject(new Error("AI provider returned an error"));
          return;
        }

        resolve({ content, model: `claude-${cliModel}` });
      });
    });
  }
}
