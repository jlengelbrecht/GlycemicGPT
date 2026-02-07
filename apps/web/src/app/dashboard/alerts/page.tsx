/**
 * Alerts Page
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Placeholder page for alerts - will be implemented in Epic 6.
 */

import { Bell } from "lucide-react";

export default function AlertsPage() {
  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold">Alerts</h1>
        <p className="text-slate-400">
          Predictive alerts and emergency notifications
        </p>
      </div>

      {/* Placeholder content */}
      <div className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center">
        <div className="flex justify-center mb-4">
          <div className="p-4 bg-amber-500/10 rounded-full">
            <Bell className="h-12 w-12 text-amber-400" />
          </div>
        </div>
        <h2 className="text-xl font-semibold mb-2">Coming Soon</h2>
        <p className="text-slate-400 max-w-md mx-auto">
          The alerting system will provide predictive glucose alerts, emergency
          escalation to caregivers, and customizable thresholds. This feature
          will be implemented in Epic 6.
        </p>
      </div>

      {/* No active alerts message */}
      <div className="bg-slate-900/50 rounded-xl p-6 border border-slate-800">
        <div className="flex items-center gap-3">
          <div className="h-3 w-3 rounded-full bg-green-500" />
          <span className="text-slate-300">No active alerts</span>
        </div>
      </div>
    </div>
  );
}
