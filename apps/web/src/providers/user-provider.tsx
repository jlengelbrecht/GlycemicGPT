"use client";

/**
 * Story 8.3: UserProvider — shared current-user context.
 *
 * Wraps the useCurrentUser hook in a React context so that
 * Sidebar, MobileNav, and page components share a single
 * GET /api/auth/me request instead of each firing their own.
 */

import { createContext, useContext } from "react";
import { useCurrentUser } from "@/hooks/use-current-user";
import type { CurrentUserResponse } from "@/lib/api";

interface UserContextValue {
  user: CurrentUserResponse | null;
  isLoading: boolean;
  error: string | null;
}

const UserContext = createContext<UserContextValue>({
  user: null,
  isLoading: true,
  error: null,
});

export function UserProvider({ children }: { children: React.ReactNode }) {
  const value = useCurrentUser();

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUserContext(): UserContextValue {
  return useContext(UserContext);
}
