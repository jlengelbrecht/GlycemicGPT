"use client";

/**
 * Story 12.1: Integrations Settings Page
 *
 * Allows users to configure Dexcom and Tandem integration credentials,
 * test connections, and view connection status.
 */

import { useState, useEffect, useCallback } from "react";
import {
  Loader2,
  AlertTriangle,
  Check,
  ArrowLeft,
  Link2,
  Unlink,
  Wifi,
  WifiOff,
  Eye,
  EyeOff,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";
import {
  listIntegrations,
  connectDexcom,
  disconnectDexcom,
  connectTandem,
  disconnectTandem,
  type IntegrationResponse,
} from "@/lib/api";

type IntegrationStatus = IntegrationResponse["status"];

const STATUS_LABELS: Record<IntegrationStatus, string> = {
  pending: "Pending",
  connected: "Connected",
  error: "Error",
  disconnected: "Not Connected",
};

const STATUS_COLORS: Record<IntegrationStatus, string> = {
  pending: "text-amber-400",
  connected: "text-green-400",
  error: "text-red-400",
  disconnected: "text-slate-500",
};

function IntegrationCard({
  title,
  description,
  status,
  lastSyncAt,
  lastError,
  onConnect,
  onDisconnect,
  isConnecting,
  fields,
}: {
  title: string;
  description: string;
  status: IntegrationStatus | null;
  lastSyncAt: string | null;
  lastError: string | null;
  onConnect: () => Promise<void>;
  onDisconnect: () => Promise<void>;
  isConnecting: boolean;
  fields: React.ReactNode;
}) {
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const isConnected = status === "connected";
  const hasIntegration = status !== null && status !== "disconnected";

  const handleDisconnect = async () => {
    setIsDisconnecting(true);
    try {
      await onDisconnect();
    } finally {
      setIsDisconnecting(false);
      setConfirmDisconnect(false);
    }
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onConnect();
  };

  return (
    <div className="bg-slate-900 rounded-xl border border-slate-800 p-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div
            className={clsx(
              "p-2 rounded-lg",
              isConnected ? "bg-green-500/10" : "bg-slate-700/50"
            )}
          >
            {isConnected ? (
              <Wifi className="h-5 w-5 text-green-400" />
            ) : (
              <WifiOff className="h-5 w-5 text-slate-500" />
            )}
          </div>
          <div>
            <h2 className="text-lg font-semibold">{title}</h2>
            <p className="text-xs text-slate-500">{description}</p>
          </div>
        </div>
        {status && (
          <span
            className={clsx("text-sm font-medium", STATUS_COLORS[status])}
          >
            {STATUS_LABELS[status]}
          </span>
        )}
      </div>

      {/* Connection details for connected integrations */}
      {hasIntegration && (
        <div className="mb-6 space-y-2">
          {lastSyncAt && (
            <div className="bg-slate-800/50 rounded-lg px-3 py-2 border border-slate-700/50">
              <p className="text-xs text-slate-500">Last synced</p>
              <p className="text-sm text-slate-300">
                {new Date(lastSyncAt).toLocaleString()}
              </p>
            </div>
          )}
          {lastError && status === "error" && (
            <div
              className="bg-red-500/10 rounded-lg px-3 py-2 border border-red-500/20"
              role="alert"
            >
              <p className="text-xs text-red-400 line-clamp-3">{lastError}</p>
            </div>
          )}
        </div>
      )}

      {/* Credential form */}
      <form onSubmit={handleFormSubmit}>
        {fields}

        {/* Action buttons */}
        <div className="flex items-center gap-3 mt-4">
          <button
            type="submit"
            disabled={isConnecting || isDisconnecting}
            className={clsx(
              "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
              "bg-blue-600 text-white hover:bg-blue-500",
              "transition-colors",
              "focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500",
              "disabled:opacity-50 disabled:cursor-not-allowed"
            )}
          >
            {isConnecting ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <Link2 className="h-4 w-4" aria-hidden="true" />
            )}
            {isConnecting
              ? "Testing..."
              : hasIntegration
                ? "Update Credentials"
                : "Test Connection"}
          </button>

          {hasIntegration && !confirmDisconnect && (
            <button
              type="button"
              onClick={() => setConfirmDisconnect(true)}
              disabled={isConnecting || isDisconnecting}
              className={clsx(
                "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
                "bg-red-600/20 text-red-400 hover:bg-red-600/30",
                "transition-colors",
                "focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500",
                "disabled:opacity-50 disabled:cursor-not-allowed"
              )}
            >
              <Unlink className="h-4 w-4" aria-hidden="true" />
              Disconnect
            </button>
          )}

          {hasIntegration && confirmDisconnect && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleDisconnect}
                disabled={isDisconnecting}
                className={clsx(
                  "flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium",
                  "bg-red-600 text-white hover:bg-red-500",
                  "transition-colors",
                  "focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500",
                  "disabled:opacity-50 disabled:cursor-not-allowed"
                )}
              >
                {isDisconnecting ? (
                  <Loader2
                    className="h-4 w-4 animate-spin"
                    aria-hidden="true"
                  />
                ) : (
                  <Unlink className="h-4 w-4" aria-hidden="true" />
                )}
                {isDisconnecting ? "Disconnecting..." : "Yes, Disconnect"}
              </button>
              <button
                type="button"
                onClick={() => setConfirmDisconnect(false)}
                disabled={isDisconnecting}
                className={clsx(
                  "px-4 py-2 rounded-lg text-sm font-medium",
                  "bg-slate-800 text-slate-300 hover:bg-slate-700",
                  "transition-colors",
                  "focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-500",
                  "disabled:opacity-50 disabled:cursor-not-allowed"
                )}
              >
                Cancel
              </button>
            </div>
          )}
        </div>
      </form>
    </div>
  );
}

function PasswordInput({
  id,
  value,
  onChange,
  disabled,
  label,
  hint,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  disabled: boolean;
  label: string;
  hint?: string;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div>
      <label
        htmlFor={id}
        className="block text-sm font-medium text-slate-300 mb-1"
      >
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          type={visible ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          autoComplete="one-time-code"
          className={clsx(
            "w-full rounded-lg border px-3 py-2 pr-10 text-sm",
            "bg-slate-800 border-slate-700 text-slate-200",
            "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
            "disabled:opacity-50 disabled:cursor-not-allowed"
          )}
        />
        <button
          type="button"
          onClick={() => setVisible(!visible)}
          className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-slate-400 hover:text-slate-300"
          aria-label={visible ? "Hide password" : "Show password"}
        >
          {visible ? (
            <EyeOff className="h-4 w-4" />
          ) : (
            <Eye className="h-4 w-4" />
          )}
        </button>
      </div>
      {hint && <p className="text-xs text-slate-500 mt-1">{hint}</p>}
    </div>
  );
}

export default function IntegrationsPage() {
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Integration state
  const [dexcom, setDexcom] = useState<IntegrationResponse | null>(null);
  const [tandem, setTandem] = useState<IntegrationResponse | null>(null);

  // Dexcom form
  const [dexcomEmail, setDexcomEmail] = useState("");
  const [dexcomPassword, setDexcomPassword] = useState("");
  const [isDexcomConnecting, setIsDexcomConnecting] = useState(false);

  // Tandem form
  const [tandemEmail, setTandemEmail] = useState("");
  const [tandemPassword, setTandemPassword] = useState("");
  const [tandemRegion, setTandemRegion] = useState("US");
  const [isTandemConnecting, setIsTandemConnecting] = useState(false);

  // Auto-clear success message
  useEffect(() => {
    if (!success) return;
    const timer = setTimeout(() => setSuccess(null), 5000);
    return () => clearTimeout(timer);
  }, [success]);

  const fetchIntegrations = useCallback(async () => {
    try {
      setError(null);
      const data = await listIntegrations();

      const dexcomInt = data.integrations.find(
        (i) => i.integration_type === "dexcom"
      );
      const tandemInt = data.integrations.find(
        (i) => i.integration_type === "tandem"
      );

      setDexcom(dexcomInt || null);
      setTandem(tandemInt || null);
    } catch (err) {
      if (!(err instanceof Error && err.message.includes("401"))) {
        setError(
          err instanceof Error
            ? err.message
            : "Failed to load integrations"
        );
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchIntegrations();
  }, [fetchIntegrations]);

  const handleConnectDexcom = async () => {
    if (!dexcomEmail || !dexcomPassword) {
      setError("Please enter your Dexcom email and password");
      return;
    }

    setIsDexcomConnecting(true);
    setError(null);
    setSuccess(null);

    try {
      const result = await connectDexcom({
        username: dexcomEmail,
        password: dexcomPassword,
      });
      setDexcom(result.integration);
      setDexcomPassword("");
      setSuccess("Dexcom connected successfully");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to connect Dexcom"
      );
    } finally {
      setIsDexcomConnecting(false);
    }
  };

  const handleDisconnectDexcom = async () => {
    setError(null);
    setSuccess(null);

    try {
      await disconnectDexcom();
      setDexcom(null);
      setDexcomEmail("");
      setDexcomPassword("");
      setSuccess("Dexcom disconnected");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to disconnect Dexcom"
      );
    }
  };

  const handleConnectTandem = async () => {
    if (!tandemEmail || !tandemPassword) {
      setError("Please enter your Tandem email and password");
      return;
    }

    setIsTandemConnecting(true);
    setError(null);
    setSuccess(null);

    try {
      const result = await connectTandem({
        username: tandemEmail,
        password: tandemPassword,
        region: tandemRegion,
      });
      setTandem(result.integration);
      setTandemPassword("");
      setSuccess("Tandem connected successfully");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to connect Tandem"
      );
    } finally {
      setIsTandemConnecting(false);
    }
  };

  const handleDisconnectTandem = async () => {
    setError(null);
    setSuccess(null);

    try {
      await disconnectTandem();
      setTandem(null);
      setTandemEmail("");
      setTandemPassword("");
      setSuccess("Tandem disconnected");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to disconnect Tandem"
      );
    }
  };

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <Link
          href="/dashboard/settings"
          className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-300 mb-2"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Settings
        </Link>
        <h1 className="text-2xl font-bold">Integrations</h1>
        <p className="text-slate-400">
          Connect your Dexcom and Tandem accounts to sync glucose and pump data
        </p>
      </div>

      {/* Error state */}
      {error && (
        <div
          className="bg-red-500/10 rounded-xl p-4 border border-red-500/20"
          role="alert"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-red-400 shrink-0" />
            <p className="text-sm text-red-400">{error}</p>
          </div>
        </div>
      )}

      {/* Success state */}
      {success && (
        <div
          className="bg-green-500/10 rounded-xl p-4 border border-green-500/20"
          role="status"
        >
          <div className="flex items-center gap-2">
            <Check className="h-4 w-4 text-green-400 shrink-0" />
            <p className="text-sm text-green-400">{success}</p>
          </div>
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div
          className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center"
          role="status"
          aria-label="Loading integrations"
        >
          <Loader2 className="h-8 w-8 text-blue-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">Loading integrations...</p>
        </div>
      )}

      {/* Dexcom Card */}
      {!isLoading && (
        <IntegrationCard
          title="Dexcom G7"
          description="Connect your Dexcom Share account to sync CGM glucose data"
          status={dexcom?.status || null}
          lastSyncAt={dexcom?.last_sync_at || null}
          lastError={dexcom?.last_error || null}
          onConnect={handleConnectDexcom}
          onDisconnect={handleDisconnectDexcom}
          isConnecting={isDexcomConnecting}
          fields={
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label
                  htmlFor="dexcom-email"
                  className="block text-sm font-medium text-slate-300 mb-1"
                >
                  Dexcom Share Email
                </label>
                <input
                  id="dexcom-email"
                  type="email"
                  value={dexcomEmail}
                  onChange={(e) => setDexcomEmail(e.target.value)}
                  disabled={isDexcomConnecting}
                  placeholder="you@example.com"
                  autoComplete="one-time-code"
                  className={clsx(
                    "w-full rounded-lg border px-3 py-2 text-sm",
                    "bg-slate-800 border-slate-700 text-slate-200",
                    "placeholder:text-slate-500",
                    "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                    "disabled:opacity-50 disabled:cursor-not-allowed"
                  )}
                />
              </div>
              <PasswordInput
                id="dexcom-password"
                value={dexcomPassword}
                onChange={setDexcomPassword}
                disabled={isDexcomConnecting}
                label="Dexcom Share Password"
              />
            </div>
          }
        />
      )}

      {/* Tandem Card */}
      {!isLoading && (
        <IntegrationCard
          title="Tandem t:connect"
          description="Connect your Tandem t:connect account to sync pump and Control-IQ data"
          status={tandem?.status || null}
          lastSyncAt={tandem?.last_sync_at || null}
          lastError={tandem?.last_error || null}
          onConnect={handleConnectTandem}
          onDisconnect={handleDisconnectTandem}
          isConnecting={isTandemConnecting}
          fields={
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label
                    htmlFor="tandem-email"
                    className="block text-sm font-medium text-slate-300 mb-1"
                  >
                    Tandem t:connect Email
                  </label>
                  <input
                    id="tandem-email"
                    type="email"
                    value={tandemEmail}
                    onChange={(e) => setTandemEmail(e.target.value)}
                    disabled={isTandemConnecting}
                    placeholder="you@example.com"
                    autoComplete="one-time-code"
                    className={clsx(
                      "w-full rounded-lg border px-3 py-2 text-sm",
                      "bg-slate-800 border-slate-700 text-slate-200",
                      "placeholder:text-slate-500",
                      "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                      "disabled:opacity-50 disabled:cursor-not-allowed"
                    )}
                  />
                </div>
                <PasswordInput
                  id="tandem-password"
                  value={tandemPassword}
                  onChange={setTandemPassword}
                  disabled={isTandemConnecting}
                  label="Tandem t:connect Password"
                />
              </div>
              <div className="max-w-xs">
                <label
                  htmlFor="tandem-region"
                  className="block text-sm font-medium text-slate-300 mb-1"
                >
                  Region
                </label>
                <select
                  id="tandem-region"
                  value={tandemRegion}
                  onChange={(e) => setTandemRegion(e.target.value)}
                  disabled={isTandemConnecting}
                  className={clsx(
                    "w-full rounded-lg border px-3 py-2 text-sm",
                    "bg-slate-800 border-slate-700 text-slate-200",
                    "focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
                    "disabled:opacity-50 disabled:cursor-not-allowed"
                  )}
                >
                  <option value="US">United States</option>
                  <option value="EU">Europe</option>
                </select>
                <p className="text-xs text-slate-500 mt-1">
                  Select your Tandem account region
                </p>
              </div>
            </div>
          }
        />
      )}

      {/* Info card */}
      <div className="bg-slate-900/50 rounded-xl p-4 border border-slate-800">
        <div className="flex items-start gap-2">
          <Link2 className="h-4 w-4 text-slate-500 mt-0.5 shrink-0" />
          <p className="text-xs text-slate-500">
            Your credentials are encrypted before storage and are only used to
            fetch your glucose and pump data. We never share your credentials
            with third parties. Connection is validated before credentials are
            saved — invalid credentials will not be stored.
          </p>
        </div>
      </div>
    </div>
  );
}
