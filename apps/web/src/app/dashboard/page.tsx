/**
 * Dashboard Page
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Main dashboard view showing glucose data and metrics.
 * Placeholder content - glucose components will be added in Stories 4.2-4.5.
 */

import { Activity, TrendingUp, Droplets, Clock } from "lucide-react";

export default function DashboardPage() {
  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-slate-400">Your glucose overview at a glance</p>
      </div>

      {/* Glucose hero placeholder - Story 4.2 */}
      <div className="bg-slate-900 rounded-xl p-8 border border-slate-800">
        <div className="flex flex-col items-center justify-center text-center">
          <div className="flex items-center gap-4 mb-4">
            <span className="text-7xl font-bold text-green-400">142</span>
            <TrendingUp className="h-12 w-12 text-green-400" />
          </div>
          <p className="text-slate-400 text-lg">mg/dL</p>
          <p className="text-slate-500 text-sm mt-2">
            Placeholder - GlucoseHero component coming in Story 4.2
          </p>
        </div>
      </div>

      {/* Metrics grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* IoB Card */}
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-blue-500/10 rounded-lg">
              <Droplets className="h-5 w-5 text-blue-400" />
            </div>
            <span className="text-slate-400 text-sm">Insulin on Board</span>
          </div>
          <p className="text-3xl font-bold">2.4u</p>
          <p className="text-slate-500 text-xs mt-1">
            Projected from last sync
          </p>
        </div>

        {/* Time in Range Card */}
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-green-500/10 rounded-lg">
              <Activity className="h-5 w-5 text-green-400" />
            </div>
            <span className="text-slate-400 text-sm">Time in Range (24h)</span>
          </div>
          <p className="text-3xl font-bold text-green-400">78%</p>
          <p className="text-slate-500 text-xs mt-1">Target: 70-180 mg/dL</p>
        </div>

        {/* Last Updated Card */}
        <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
          <div className="flex items-center gap-3 mb-2">
            <div className="p-2 bg-emerald-500/10 rounded-lg">
              <Clock className="h-5 w-5 text-emerald-400" />
            </div>
            <span className="text-slate-400 text-sm">Last Updated</span>
          </div>
          <p className="text-3xl font-bold text-emerald-400">2m ago</p>
          <p className="text-slate-500 text-xs mt-1">Data is fresh</p>
        </div>
      </div>

      {/* Time in Range bar placeholder - Story 4.4 */}
      <div className="bg-slate-900 rounded-xl p-6 border border-slate-800">
        <h2 className="text-lg font-semibold mb-4">Time in Range</h2>
        <div className="h-8 rounded-full overflow-hidden flex">
          <div className="bg-red-500 w-[5%]" title="Below range: 5%" />
          <div className="bg-green-500 w-[78%]" title="In range: 78%" />
          <div className="bg-orange-500 w-[17%]" title="Above range: 17%" />
        </div>
        <div className="flex justify-between mt-2 text-xs text-slate-500">
          <span>Low: 5%</span>
          <span>In Range: 78%</span>
          <span>High: 17%</span>
        </div>
        <p className="text-slate-500 text-xs mt-4 text-center">
          Placeholder - TimeInRangeBar component coming in Story 4.4
        </p>
      </div>
    </div>
  );
}
