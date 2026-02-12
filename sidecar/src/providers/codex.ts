/**
 * OpenAI Codex CLI subprocess wrapper.
 *
 * Spawns `codex` CLI and translates between OpenAI chat format and CLI I/O.
 *
 * Authentication: reads from ~/.codex/auth.json (mounted volume) or
 * OPENAI_API_KEY env var as fallback for direct API usage.
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

const CODEX_HOME = process.env.CODEX_HOME || join(
  process.env.HOME || "/home/sidecar",
  ".codex",
);
const AUTH_FILE = join(CODEX_HOME, "auth.json");

/** Subprocess timeout (2 minutes) */
const SUBPROCESS_TIMEOUT_MS = 120_000;
/** Maximum buffer size (10 MB) */
const MAX_BUFFER_BYTES = 10 * 1024 * 1024;
/** Maximum prompt length (100 KB) */
const MAX_PROMPT_LENGTH = 100_000;

/** Strict allowlist of model names */
const MODEL_MAP: Record<string, string> = {
  "gpt-4o": "gpt-4o",
  "gpt-4": "gpt-4",
  "gpt-4-turbo": "gpt-4-turbo",
  "o3-mini": "o3-mini",
  "chatgpt-subscription": "gpt-4o",
};

/** Resolve model name. Rejects unknown models. */
export function resolveModel(model?: string): string {
  if (!model) return "gpt-4o";
  const resolved = MODEL_MAP[model];
  if (!resolved) {
    throw new Error(`Unsupported model: ${model}`);
  }
  return resolved;
}

/** Check if Codex auth.json exists and contains a valid access token */
function getAuthState(): { authenticated: boolean } {
  if (process.env.OPENAI_API_KEY) {
    return { authenticated: true };
  }

  try {
    if (existsSync(AUTH_FILE)) {
      const data = JSON.parse(readFileSync(AUTH_FILE, "utf-8"));
      if (data.accessToken) {
        if (data.expiresAt && Date.now() / 1000 > data.expiresAt) {
          return { authenticated: false };
        }
        return { authenticated: true };
      }
    }
  } catch {
    // File unreadable or invalid JSON
  }
  return { authenticated: false };
}

/** Flatten messages into a prompt for the CLI */
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

/** Spawn the Codex CLI, passing prompt via stdin */
function spawnCodex(prompt: string, model: string): ChildProcess {
  const env: Record<string, string> = { ...process.env } as Record<
    string,
    string
  >;
  env.CODEX_HOME = CODEX_HOME;

  const child = spawn(
    "codex",
    [
      "--model",
      model, // Already validated by resolveModel()
      "--quiet",
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

/** Kill a child process and clear its timeout */
function cleanupChild(child: ChildProcess, timer: ReturnType<typeof setTimeout>): void {
  clearTimeout(timer);
  if (!child.killed) child.kill();
}

export class CodexProvider implements AIProvider {
  async checkAuth(): Promise<ProviderAuthState> {
    const { authenticated } = getAuthState();
    return {
      authenticated,
      provider: "codex",
      message: authenticated
        ? "Codex authentication configured"
        : "No Codex authentication found",
    };
  }

  async complete(
    messages: ChatMessage[],
    model?: string,
  ): Promise<ProviderResult> {
    const prompt = messagesToPrompt(messages);
    const cliModel = resolveModel(model);

    return new Promise((resolve, reject) => {
      const child = spawnCodex(prompt, cliModel);
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
        reject(new Error(`Codex CLI failed to start: ${err.message}`));
      });

      child.on("close", (code) => {
        clearTimeout(timer);
        if (code !== 0) {
          reject(new Error("AI provider returned an error"));
          return;
        }
        resolve({ content: stdout.trim(), model: cliModel });
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
      const child = spawnCodex(prompt, cliModel);
      let content = "";
      let totalSize = 0;
      let stderrSize = 0;

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
        const text = chunk.toString();
        content += text;
        onChunk?.(text);
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
        reject(new Error(`Codex CLI failed to start: ${err.message}`));
      });

      child.on("close", (code) => {
        clearTimeout(timer);
        if (code !== 0 && !content) {
          reject(new Error("AI provider returned an error"));
          return;
        }
        resolve({ content: content.trim(), model: cliModel });
      });
    });
  }
}
