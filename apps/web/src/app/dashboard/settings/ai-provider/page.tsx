/**
 * AI Provider Configuration Page
 *
 * Story 11.1: Create AI Provider Configuration Page
 *
 * Allows users to configure their AI provider (Claude or OpenAI),
 * enter API keys, test connections, and manage their AI configuration.
 */

"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Brain,
  CheckCircle2,
  Eye,
  EyeOff,
  Key,
  Loader2,
  Trash2,
  Wifi,
  WifiOff,
  Zap,
} from "lucide-react";
import {
  getAIProvider,
  configureAIProvider,
  testAIProvider,
  deleteAIProvider,
  type AIProviderConfigResponse,
  type AIProviderType,
  type AIProviderStatus,
} from "@/lib/api";
import { OfflineBanner } from "@/components/ui/offline-banner";

const PROVIDER_OPTIONS: { value: AIProviderType; label: string; description: string }[] = [
  {
    value: "claude",
    label: "Claude (Anthropic)",
    description: "Recommended. Supports Claude Sonnet, Opus, and Haiku models.",
  },
  {
    value: "openai",
    label: "OpenAI",
    description: "Supports GPT-4o and other OpenAI models.",
  },
];

const STATUS_CONFIG: Record<AIProviderStatus, { label: string; color: string; bg: string }> = {
  connected: { label: "Connected", color: "text-green-400", bg: "bg-green-500/10" },
  error: { label: "Error", color: "text-red-400", bg: "bg-red-500/10" },
  pending: { label: "Pending", color: "text-amber-400", bg: "bg-amber-500/10" },
};

export default function AIProviderPage() {
  const [isLoading, setIsLoading] = useState(true);
  const [config, setConfig] = useState<AIProviderConfigResponse | null>(null);
  const [isOffline, setIsOffline] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Form state
  const [providerType, setProviderType] = useState<AIProviderType>("claude");
  const [apiKey, setApiKey] = useState("");
  const [showApiKey, setShowApiKey] = useState(false);
  const [modelName, setModelName] = useState("");

  // Action state
  const [isSaving, setIsSaving] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Auto-clear success message
  useEffect(() => {
    if (!success) return;
    const timer = setTimeout(() => setSuccess(null), 5000);
    return () => clearTimeout(timer);
  }, [success]);

  const fetchConfig = useCallback(async () => {
    try {
      const data = await getAIProvider();
      setConfig(data);
      setProviderType(data.provider_type);
      setModelName(data.model_name || "");
      setIsOffline(false);
    } catch (err) {
      const is401 = err instanceof Error && err.message.includes("401");
      const is404 = err instanceof Error && err.message.includes("404");
      if (is404) {
        // No provider configured - that's fine
        setConfig(null);
        setIsOffline(false);
      } else if (!is401) {
        setIsOffline(true);
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchConfig();
  }, [fetchConfig]);

  const handleSave = async () => {
    if (!apiKey.trim()) {
      setError("Please enter an API key");
      return;
    }

    setIsSaving(true);
    setError(null);
    setSuccess(null);

    try {
      const result = await configureAIProvider({
        provider_type: providerType,
        api_key: apiKey.trim(),
        model_name: modelName.trim() || null,
      });
      setConfig(result);
      setApiKey("");
      setSuccess("AI provider configured successfully");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to configure AI provider"
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleTest = async () => {
    setIsTesting(true);
    setError(null);
    setSuccess(null);

    try {
      const result = await testAIProvider();
      if (result.success) {
        setSuccess(result.message);
        // Refresh config to get updated status
        await fetchConfig();
      } else {
        setError(result.message);
        await fetchConfig();
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to test AI provider"
      );
    } finally {
      setIsTesting(false);
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    setError(null);
    setSuccess(null);

    try {
      await deleteAIProvider();
      setConfig(null);
      setConfirmDelete(false);
      setApiKey("");
      setModelName("");
      setProviderType("claude");
      setSuccess("AI provider configuration removed");
    } catch (err) {
      setConfirmDelete(false);
      setError(
        err instanceof Error ? err.message : "Failed to remove AI provider"
      );
    } finally {
      setIsDeleting(false);
    }
  };

  const isConfigured = config !== null;
  const statusInfo = config ? STATUS_CONFIG[config.status] : null;

  return (
    <div className="space-y-6 max-w-2xl">
      {/* Back link */}
      <Link
        href="/dashboard/settings"
        className="inline-flex items-center gap-2 text-slate-400 hover:text-white transition-colors text-sm"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Settings
      </Link>

      {/* Page header */}
      <div className="flex items-center gap-3">
        <div className="p-3 bg-slate-800 rounded-lg">
          <Brain className="h-6 w-6 text-purple-400" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">AI Provider</h1>
          <p className="text-slate-400 text-sm">
            Configure your AI provider for glucose analysis and insights
          </p>
        </div>
      </div>

      {/* Offline banner */}
      {isOffline && (
        <OfflineBanner
          onRetry={async () => {
            setIsRetrying(true);
            await fetchConfig();
            setIsRetrying(false);
          }}
          isRetrying={isRetrying}
          message="Unable to connect to server. AI provider settings are unavailable."
        />
      )}

      {/* Error banner */}
      {error && (
        <div
          role="alert"
          className="bg-red-500/10 border border-red-500/30 text-red-400 rounded-lg px-4 py-3 text-sm"
        >
          {error}
        </div>
      )}

      {/* Success banner */}
      {success && (
        <div
          role="status"
          className="bg-green-500/10 border border-green-500/30 text-green-400 rounded-lg px-4 py-3 text-sm"
        >
          {success}
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div
          className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center"
          role="status"
          aria-label="Loading AI provider configuration"
        >
          <Loader2 className="h-8 w-8 text-purple-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">Loading AI configuration...</p>
        </div>
      )}

      {/* Current configuration status */}
      {!isLoading && isConfigured && (
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {config.status === "connected" ? (
                <Wifi className="h-5 w-5 text-green-400" />
              ) : (
                <WifiOff className="h-5 w-5 text-red-400" />
              )}
              <h2 className="text-lg font-semibold">Current Configuration</h2>
            </div>
            {statusInfo && (
              <span
                className={`inline-flex items-center gap-1.5 ${statusInfo.bg} ${statusInfo.color} text-xs font-medium px-2.5 py-1 rounded-full`}
              >
                <CheckCircle2 className="h-3.5 w-3.5" />
                {statusInfo.label}
              </span>
            )}
          </div>

          <div className="bg-slate-800 rounded-lg p-4 space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">Provider</span>
              <span className="text-white font-medium">
                {config.provider_type === "claude" ? "Claude (Anthropic)" : "OpenAI"}
              </span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-slate-400">API Key</span>
              <span className="text-white font-mono text-xs">
                {config.masked_api_key}
              </span>
            </div>
            {config.model_name && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-400">Model</span>
                <span className="text-white font-mono text-xs">
                  {config.model_name}
                </span>
              </div>
            )}
            {config.last_validated_at && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-400">Last Validated</span>
                <span className="text-white text-xs">
                  {new Date(config.last_validated_at).toLocaleString()}
                </span>
              </div>
            )}
            {config.last_error && (
              <div className="bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2 mt-2">
                <p className="text-xs text-red-400">{config.last_error}</p>
              </div>
            )}
          </div>

          {/* Action buttons for configured state */}
          <div className="flex flex-col gap-2">
            <button
              onClick={handleTest}
              disabled={isTesting || isDeleting || isOffline}
              title={isOffline ? "Cannot test while disconnected" : undefined}
              className="w-full bg-slate-800 hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium rounded-lg px-4 py-3 transition-colors flex items-center justify-center gap-2"
              aria-label="Test connection"
            >
              {isTesting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Zap className="h-4 w-4" />
              )}
              Test Connection
            </button>

            {!confirmDelete ? (
              <button
                onClick={() => setConfirmDelete(true)}
                disabled={isTesting || isDeleting || isOffline}
                title={isOffline ? "Cannot remove while disconnected" : undefined}
                className="w-full text-red-400 hover:text-red-300 disabled:opacity-50 disabled:cursor-not-allowed text-sm transition-colors flex items-center justify-center gap-2 py-2"
                aria-label="Remove AI provider"
              >
                <Trash2 className="h-4 w-4" />
                Remove AI Provider
              </button>
            ) : (
              <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-4 space-y-3">
                <p className="text-red-400 text-sm">
                  Are you sure? Removing the AI provider will disable all AI
                  features including daily briefs, insights, and chat.
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={handleDelete}
                    disabled={isDeleting}
                    className="flex-1 bg-red-600 hover:bg-red-500 disabled:opacity-50 text-white text-sm font-medium rounded-lg px-3 py-2 transition-colors"
                  >
                    {isDeleting ? (
                      <Loader2 className="h-4 w-4 animate-spin mx-auto" />
                    ) : (
                      "Yes, Remove"
                    )}
                  </button>
                  <button
                    onClick={() => setConfirmDelete(false)}
                    className="flex-1 bg-slate-800 hover:bg-slate-700 text-white text-sm font-medium rounded-lg px-3 py-2 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Configuration form */}
      {!isLoading && (
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800 space-y-5">
          <div className="flex items-center gap-2">
            <Key className="h-5 w-5 text-purple-400" />
            <h2 className="text-lg font-semibold">
              {isConfigured ? "Update Configuration" : "Set Up AI Provider"}
            </h2>
          </div>

          {!isConfigured && (
            <p className="text-slate-400 text-sm">
              GlycemicGPT uses your own AI API key (BYOAI) to analyze glucose
              data and generate insights. Your key is encrypted before storage
              and never shared.
            </p>
          )}

          {/* Provider selection */}
          <div className="space-y-3">
            <label className="block text-sm font-medium text-slate-300">
              AI Provider
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {PROVIDER_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setProviderType(option.value)}
                  disabled={isOffline}
                  className={`text-left p-4 rounded-lg border transition-colors ${
                    providerType === option.value
                      ? "border-purple-500 bg-purple-500/10"
                      : "border-slate-700 bg-slate-800 hover:border-slate-600"
                  } disabled:opacity-50 disabled:cursor-not-allowed`}
                  aria-pressed={providerType === option.value}
                  aria-label={`Select ${option.label}`}
                >
                  <p className="text-sm font-medium text-white">
                    {option.label}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">
                    {option.description}
                  </p>
                </button>
              ))}
            </div>
          </div>

          {/* API Key input */}
          <div className="space-y-2">
            <label
              htmlFor="api-key"
              className="block text-sm font-medium text-slate-300"
            >
              API Key
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Key className="h-4 w-4 text-slate-500" />
              </div>
              <input
                id="api-key"
                type={showApiKey ? "text" : "password"}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={
                  providerType === "claude"
                    ? "Enter your Anthropic API key"
                    : "Enter your OpenAI API key"
                }
                disabled={isOffline || isSaving}
                autoComplete="off"
                className="w-full bg-slate-800 border border-slate-700 rounded-lg pl-10 pr-12 py-3 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent disabled:opacity-50 font-mono text-sm"
              />
              <button
                type="button"
                onClick={() => setShowApiKey(!showApiKey)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-white transition-colors"
                aria-label={showApiKey ? "Hide API key" : "Show API key"}
              >
                {showApiKey ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            </div>
            <p className="text-xs text-slate-500">
              {providerType === "claude"
                ? "Get your API key from console.anthropic.com"
                : "Get your API key from platform.openai.com"}
            </p>
          </div>

          {/* Model override (optional) */}
          <div className="space-y-2">
            <label
              htmlFor="model-name"
              className="block text-sm font-medium text-slate-300"
            >
              Model Name{" "}
              <span className="text-slate-500 font-normal">(optional)</span>
            </label>
            <input
              id="model-name"
              type="text"
              value={modelName}
              onChange={(e) => setModelName(e.target.value)}
              placeholder={
                providerType === "claude"
                  ? "claude-sonnet-4-5-20250929"
                  : "gpt-4o"
              }
              disabled={isOffline || isSaving}
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent disabled:opacity-50 text-sm"
            />
            <p className="text-xs text-slate-500">
              Leave blank to use the default model. Override with a specific
              model ID if needed.
            </p>
          </div>

          {/* Save button */}
          <button
            onClick={handleSave}
            disabled={!apiKey.trim() || isSaving || isOffline}
            title={
              isOffline
                ? "Cannot save while disconnected"
                : !apiKey.trim()
                  ? "Enter an API key first"
                  : undefined
            }
            className="w-full bg-purple-600 hover:bg-purple-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium rounded-lg px-4 py-3 transition-colors flex items-center justify-center gap-2"
            aria-label={isConfigured ? "Update AI provider" : "Save and validate API key"}
          >
            {isSaving ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <CheckCircle2 className="h-4 w-4" />
            )}
            {isConfigured ? "Update API Key" : "Save & Validate"}
          </button>
        </div>
      )}

      {/* Info card */}
      <div className="bg-slate-900/50 rounded-xl p-4 border border-slate-800">
        <div className="flex items-start gap-2">
          <Brain className="h-4 w-4 text-slate-500 mt-0.5 shrink-0" />
          <p className="text-xs text-slate-500">
            Your API key is encrypted before storage and is only used to
            communicate with your chosen AI provider. We never share your key
            with third parties. The key is validated before being saved —
            invalid keys will not be stored.
          </p>
        </div>
      </div>
    </div>
  );
}
