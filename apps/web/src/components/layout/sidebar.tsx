"use client";

/**
 * Sidebar Navigation Component
 *
 * Story 4.1: Dashboard Layout & Navigation
 * Story 8.3: Role-aware navigation for caregiver accounts
 * Story 8.6: Caregivers see only the Caregiver Dashboard link
 * Provides navigation to Dashboard, Daily Briefs, Alerts, and Settings.
 * Caregivers see only the Caregiver Dashboard (read-only enforcement).
 * Collapses to hamburger menu on mobile.
 */

import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { clsx } from "clsx";
import {
  LayoutDashboard,
  FileText,
  Bell,
  MessageSquare,
  Settings,
  Menu,
  X,
} from "lucide-react";
import { useUserContext } from "@/providers";

interface NavItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
}

const diabeticNavigation: NavItem[] = [
  { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
  { name: "Daily Briefs", href: "/dashboard/briefs", icon: FileText },
  { name: "Alerts", href: "/dashboard/alerts", icon: Bell },
  { name: "AI Chat", href: "/dashboard/ai-chat", icon: MessageSquare },
  { name: "Settings", href: "/dashboard/settings", icon: Settings },
];

const caregiverNavigation: NavItem[] = [
  { name: "Dashboard", href: "/dashboard/caregiver", icon: LayoutDashboard },
];

interface SidebarProps {
  className?: string;
}

export function Sidebar({ className }: SidebarProps) {
  const pathname = usePathname();
  const { user } = useUserContext();
  const navigation =
    user?.role === "caregiver" ? caregiverNavigation : diabeticNavigation;

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
        <Image
          src="/logo.png"
          alt="GlycemicGPT"
          width={32}
          height={32}
          className="rounded"
        />
        <span className="text-xl font-bold">GlycemicGPT</span>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-4 py-4 space-y-1">
        {navigation.map((item) => {
          const isActive =
            pathname === item.href ||
            (item.href !== "/dashboard" &&
              item.href !== "/dashboard/caregiver" &&
              pathname.startsWith(item.href));

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
  const { user } = useUserContext();
  const navigation =
    user?.role === "caregiver" ? caregiverNavigation : diabeticNavigation;

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
                <Image
                  src="/logo.png"
                  alt="GlycemicGPT"
                  width={32}
                  height={32}
                  className="rounded"
                />
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
                  (item.href !== "/dashboard" &&
                    item.href !== "/dashboard/caregiver" &&
                    pathname.startsWith(item.href));

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
