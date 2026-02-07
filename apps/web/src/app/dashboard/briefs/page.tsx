/**
 * Daily Briefs Page
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Placeholder page for daily briefs - will be implemented in Epic 5.
 */

import { FileText } from "lucide-react";

export default function BriefsPage() {
  return (
    <div className="space-y-6">
      {/* Page header */}
      <div>
        <h1 className="text-2xl font-bold">Daily Briefs</h1>
        <p className="text-slate-400">
          AI-powered analysis of your glucose patterns
        </p>
      </div>

      {/* Placeholder content */}
      <div className="bg-slate-900 rounded-xl p-12 border border-slate-800 text-center">
        <div className="flex justify-center mb-4">
          <div className="p-4 bg-blue-500/10 rounded-full">
            <FileText className="h-12 w-12 text-blue-400" />
          </div>
        </div>
        <h2 className="text-xl font-semibold mb-2">Coming Soon</h2>
        <p className="text-slate-400 max-w-md mx-auto">
          Daily briefs will provide AI-generated summaries of your glucose
          patterns, trends, and actionable suggestions. This feature will be
          implemented in Epic 5.
        </p>
      </div>
    </div>
  );
}
