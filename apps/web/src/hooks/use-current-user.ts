/**
 * Story 8.3: useCurrentUser hook
 *
 * Fetches and caches the current user's profile (id, email, role).
 * Used by dashboard pages to determine role-based rendering.
 */

"use client";

import { useState, useEffect } from "react";
import { getCurrentUser, type CurrentUserResponse } from "@/lib/api";

interface UseCurrentUserReturn {
  user: CurrentUserResponse | null;
  isLoading: boolean;
  error: string | null;
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

  return { user, isLoading, error };
}
