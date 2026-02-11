/**
 * Story 8.3: useCurrentUser hook
 *
 * Fetches and caches the current user's profile (id, email, role).
 * Used by dashboard pages to determine role-based rendering.
 *
 * Story 15.5: Added refreshUser to allow re-fetching after disclaimer acknowledgment.
 */

"use client";

import { useState, useEffect, useCallback } from "react";
import { getCurrentUser, type CurrentUserResponse } from "@/lib/api";

interface UseCurrentUserReturn {
  user: CurrentUserResponse | null;
  isLoading: boolean;
  error: string | null;
  refreshUser: () => Promise<void>;
}

export function useCurrentUser(): UseCurrentUserReturn {
  const [user, setUser] = useState<CurrentUserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchUser() {
      try {
        const data = await getCurrentUser();
        if (!cancelled) {
          setUser(data);
          setError(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to fetch user"
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchUser();

    return () => {
      cancelled = true;
    };
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const data = await getCurrentUser();
      setUser(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch user");
    }
  }, []);

  return { user, isLoading, error, refreshUser };
}
