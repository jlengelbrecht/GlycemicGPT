/**
 * Dashboard Layout
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Story 4.5: Real-Time Updates via SSE
 * Story 4.6: Dashboard Accessibility
 * Wraps all dashboard pages with the DashboardLayout component
 * and GlucoseStreamProvider for real-time glucose data.
 *
 * Accessibility features:
 * - Skip link for keyboard navigation
 */

import { DashboardLayout } from "@/components/layout";
import { GlucoseStreamProvider } from "@/providers";

/**
 * Skip link component for keyboard navigation.
 * Allows users to skip repetitive navigation and jump to main content.
 */
function SkipLink() {
  return (
    <a
      href="#main-content"
      className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2 focus:bg-blue-600 focus:text-white focus:rounded-md focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-2"
    >
      Skip to main content
    </a>
  );
}

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <SkipLink />
      <GlucoseStreamProvider>
        <DashboardLayout>{children}</DashboardLayout>
      </GlucoseStreamProvider>
    </>
  );
}
