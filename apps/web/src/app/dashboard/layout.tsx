/**
 * Dashboard Layout
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Wraps all dashboard pages with the DashboardLayout component.
 */

import { DashboardLayout } from "@/components/layout";

export default function Layout({ children }: { children: React.ReactNode }) {
  return <DashboardLayout>{children}</DashboardLayout>;
}
