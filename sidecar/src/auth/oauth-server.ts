/**
 * OAuth authentication routes for the sidecar.
 *
 * Story 15.1: Basic structure with status endpoint.
 * Story 15.2: Full browser popup OAuth flow (start, callback).
 */

import { Router, type Request, type Response } from "express";
import {
  readClaudeToken,
  readCodexAuth,
  revokeClaudeToken,
  revokeCodexAuth,
} from "./token-store.js";

export const authRouter = Router();

const VALID_PROVIDERS = new Set(["claude", "codex"]);

/** GET /auth/status - Check current authentication state */
authRouter.get("/status", (_req: Request, res: Response) => {
  const claudeToken = readClaudeToken();
  const codexAuth = readCodexAuth();

  res.json({
    claude: {
      authenticated: !!claudeToken,
    },
    codex: {
      authenticated: !!(codexAuth && (codexAuth as Record<string, unknown>).accessToken),
    },
  });
});

/**
 * POST /auth/start - Initiate OAuth flow for a provider.
 *
 * Stub for Story 15.1. Full implementation in Story 15.2.
 */
authRouter.post("/start", (req: Request, res: Response) => {
  const { provider } = req.body as { provider?: string };

  if (!provider || !VALID_PROVIDERS.has(provider)) {
    res.status(400).json({
      error: "Invalid provider. Must be 'claude' or 'codex'.",
    });
    return;
  }

  // Story 15.2 will implement the actual OAuth flow here.
  res.status(501).json({
    error: "OAuth flow not yet implemented. Use token-based auth for now.",
    provider,
  });
});

/**
 * GET /auth/callback - OAuth callback receiver.
 *
 * Stub for Story 15.1. Full implementation in Story 15.2.
 */
authRouter.get("/callback", (_req: Request, res: Response) => {
  res.status(501).json({
    error: "OAuth callback not yet implemented.",
  });
});

/**
 * POST /auth/revoke - Revoke stored authentication.
 */
authRouter.post("/revoke", (req: Request, res: Response) => {
  const { provider } = req.body as { provider?: string };

  if (!provider || !VALID_PROVIDERS.has(provider)) {
    res.status(400).json({
      error: "Invalid provider. Must be 'claude' or 'codex'.",
    });
    return;
  }

  if (provider === "claude") {
    revokeClaudeToken();
  } else {
    revokeCodexAuth();
  }

  res.json({ revoked: true, provider });
});
