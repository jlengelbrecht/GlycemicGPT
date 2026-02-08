"use client";

/**
 * GlucoseStreamProvider
 *
 * Story 4.5: Real-Time Updates via SSE
 * React context provider for sharing glucose stream data across components.
 */

import { createContext, useContext, useMemo, type ReactNode } from "react";

import {
  useGlucoseStream,
  type GlucoseData,
  type ConnectionState,
} from "@/hooks/use-glucose-stream";

/** Context value for glucose stream */
export interface GlucoseStreamContextValue {
  /** Current glucose data (null if not yet received) */
  glucose: GlucoseData | null;
  /** Current connection state */
  connectionState: ConnectionState;
  /** Whether the stream is connected and receiving data */
  isLive: boolean;
  /** Whether the stream is attempting to reconnect */
  isReconnecting: boolean;
  /** Last error that occurred (if any) */
  error: Error | null;
  /** Timestamp of the last successful data update */
  lastUpdated: Date | null;
  /** Manually reconnect the stream */
  reconnect: () => void;
  /** Manually disconnect the stream */
  disconnect: () => void;
}

const GlucoseStreamContext = createContext<GlucoseStreamContextValue | null>(null);

export interface GlucoseStreamProviderProps {
  /** Child components that will have access to the context */
  children: ReactNode;
  /** Whether the SSE connection should be active (default: true) */
  enabled?: boolean;
}

/**
 * Provider component that manages the glucose stream connection
 * and makes data available to child components via context.
 */
export function GlucoseStreamProvider({
  children,
  enabled = true,
}: GlucoseStreamProviderProps) {
  const stream = useGlucoseStream(enabled);

  // Issue 5 fix: Use individual dependencies instead of the whole stream object
  // This prevents unnecessary re-renders when memoizing
  const value = useMemo<GlucoseStreamContextValue>(
    () => ({
      glucose: stream.data,
      connectionState: stream.connectionState,
      isLive: stream.isConnected,
      isReconnecting: stream.isReconnecting,
      error: stream.error,
      lastUpdated: stream.lastUpdated,
      reconnect: stream.reconnect,
      disconnect: stream.disconnect,
    }),
    [
      stream.data,
      stream.connectionState,
      stream.isConnected,
      stream.isReconnecting,
      stream.error,
      stream.lastUpdated,
      stream.reconnect,
      stream.disconnect,
    ]
  );

  return (
    <GlucoseStreamContext.Provider value={value}>
      {children}
    </GlucoseStreamContext.Provider>
  );
}

/**
 * Hook to access the glucose stream context.
 *
 * @throws Error if used outside of GlucoseStreamProvider
 * @returns GlucoseStreamContextValue
 */
export function useGlucoseStreamContext(): GlucoseStreamContextValue {
  const context = useContext(GlucoseStreamContext);

  if (!context) {
    throw new Error(
      "useGlucoseStreamContext must be used within a GlucoseStreamProvider"
    );
  }

  return context;
}

export default GlucoseStreamProvider;
