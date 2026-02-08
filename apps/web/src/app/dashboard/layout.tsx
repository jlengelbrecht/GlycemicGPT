/**
 * Dashboard Layout
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Story 4.5: Real-Time Updates via SSE
 * Wraps all dashboard pages with the DashboardLayout component
 * and GlucoseStreamProvider for real-time glucose data.
 */

import { DashboardLayout } from "@/components/layout";
import { GlucoseStreamProvider } from "@/providers";

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <GlucoseStreamProvider>
      <DashboardLayout>{children}</DashboardLayout>
    </GlucoseStreamProvider>
  );
}
