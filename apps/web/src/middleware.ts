/**
 * Story 15.3: Next.js Auth Middleware & Route Protection
 *
 * Protects /dashboard/* routes by checking for the session cookie.
 * Redirects authenticated users away from /login and /register.
 * Cookie presence check only - the API validates the actual JWT.
 */

import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const SESSION_COOKIE = "glycemicgpt_session";

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasSession = request.cookies.has(SESSION_COOKIE);

  // Protected routes: redirect unauthenticated users to login
  if (pathname === "/dashboard" || pathname.startsWith("/dashboard/")) {
    if (!hasSession) {
      const loginUrl = new URL("/login", request.url);
      loginUrl.searchParams.set("redirect", pathname);
      return NextResponse.redirect(loginUrl);
    }
  }

  // Auth pages: redirect authenticated users to dashboard
  if (pathname === "/login" || pathname === "/register") {
    if (hasSession) {
      return NextResponse.redirect(new URL("/dashboard", request.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/login", "/register"],
};
