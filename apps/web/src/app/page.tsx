"use client";

/**
 * GlycemicGPT Home Page.
 *
 * Story 1.3: First-Run Safety Disclaimer
 * Shows the disclaimer modal on first visit.
 */

import Image from "next/image";
import { DisclaimerModal } from "@/components/disclaimer-modal";

export default function Home() {
  return (
    <>
      <DisclaimerModal />
      <main className="flex min-h-screen flex-col items-center justify-center p-8">
        <div className="text-center">
          <div className="flex justify-center mb-6">
            <Image
              src="/logo.png"
              alt="GlycemicGPT Logo"
              width={120}
              height={120}
              className="rounded-2xl"
              priority
            />
          </div>
          <h1 className="text-4xl font-bold mb-4">GlycemicGPT</h1>
          <p className="text-xl text-gray-400 mb-8">
            Your on-call endo at home
          </p>
          <div className="bg-slate-800 rounded-lg p-6 max-w-md">
            <p className="text-gray-300 mb-4">
              AI-powered diabetes management platform connecting your CGM and
              pump data with actionable insights.
            </p>
            <div className="flex gap-4 justify-center">
              <a
                href="/login"
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
              >
                Get Started
              </a>
            </div>
          </div>
        </div>
      </main>
    </>
  );
}
