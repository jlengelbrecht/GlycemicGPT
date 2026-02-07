"use client";

/**
 * Sidebar Navigation Component
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Provides navigation to Dashboard, Daily Briefs, Alerts, and Settings.
 * Collapses to hamburger menu on mobile.
 */

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { clsx } from "clsx";
import {
  LayoutDashboard,
  FileText,
  Bell,
  Settings,
  Menu,
  X,
  Activity,
} from "lucide-react";

interface NavItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
}

const navigation: NavItem[] = [
  { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { name: "Daily Briefs", href: "/dashboard/briefs", icon: FileText },
  { name: "Alerts", href: "/dashboard/alerts", icon: Bell },
  { name: "Settings", href: "/dashboard/settings", icon: Settings },
];

interface SidebarProps {
  className?: string;
}

export function Sidebar({ className }: SidebarProps) {
  const pathname = usePathname();

  return (
    <aside
      className={clsx(
        "hidden lg:flex lg:flex-col lg:w-64 lg:fixed lg:inset-y-0",
        "bg-slate-900 border-r border-slate-800",
        className
      )}
    >
      {/* Logo */}
      <div className="flex items-center gap-2 h-16 px-6 border-b border-slate-800">
        <Activity className="h-8 w-8 text-blue-500" />
        <span className="text-xl font-bold">GlycemicGPT</span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-4 py-4 space-y-1">
        {navigation.map((item) => {
          const isActive =
            pathname === item.href ||
            (item.href !== "/dashboard" && pathname.startsWith(item.href));

          return (
            <Link
              key={item.name}
              href={item.href}
              className={clsx(
                "flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors",
                isActive
                  ? "bg-blue-600 text-white"
                  : "text-slate-400 hover:text-white hover:bg-slate-800"
              )}
            >
              <item.icon className="h-5 w-5" />
              {item.name}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="px-4 py-4 border-t border-slate-800">
        <p className="text-xs text-slate-500 text-center">
          Not medical advice
        </p>
      </div>
    </aside>
  );
}

export function MobileNav() {
  const [isOpen, setIsOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      {/* Mobile menu button */}
      <button
        type="button"
        onClick={() => setIsOpen(true)}
        className="lg:hidden p-2 text-slate-400 hover:text-white"
        aria-label="Open navigation menu"
      >
        <Menu className="h-6 w-6" />
      </button>

      {/* Mobile menu overlay */}
      {isOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-black/50"
            onClick={() => setIsOpen(false)}
          />

          {/* Sidebar */}
          <div className="fixed inset-y-0 left-0 w-64 bg-slate-900 shadow-xl">
            {/* Header */}
            <div className="flex items-center justify-between h-16 px-4 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <Activity className="h-8 w-8 text-blue-500" />
                <span className="text-xl font-bold">GlycemicGPT</span>
              </div>
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                className="p-2 text-slate-400 hover:text-white"
                aria-label="Close navigation menu"
              >
                <X className="h-6 w-6" />
              </button>
            </div>

            {/* Navigation */}
            <nav className="px-4 py-4 space-y-1">
              {navigation.map((item) => {
                const isActive =
                  pathname === item.href ||
                  (item.href !== "/dashboard" && pathname.startsWith(item.href));

                return (
                  <Link
                    key={item.name}
                    href={item.href}
                    onClick={() => setIsOpen(false)}
                    className={clsx(
                      "flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors",
                      isActive
                        ? "bg-blue-600 text-white"
                        : "text-slate-400 hover:text-white hover:bg-slate-800"
                    )}
                  >
                    <item.icon className="h-5 w-5" />
                    {item.name}
                  </Link>
                );
              })}
            </nav>

            {/* Footer */}
            <div className="absolute bottom-0 left-0 right-0 px-4 py-4 border-t border-slate-800">
              <p className="text-xs text-slate-500 text-center">
                Not medical advice
              </p>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
