/**
 * Singleton provider instances.
 *
 * All modules that need provider access should import from here
 * to avoid creating duplicate instances.
 */

import { ClaudeProvider } from "./claude.js";
import { CodexProvider } from "./codex.js";

export const claude = new ClaudeProvider();
export const codex = new CodexProvider();
