import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/features/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        // Glucose range colors
        glucose: {
          low: "#ef4444",      // red-500 - urgent low
          warning: "#f59e0b",  // amber-500 - low warning
          target: "#22c55e",   // green-500 - in range
          high: "#f97316",     // orange-500 - high warning
          urgent: "#dc2626",   // red-600 - urgent high
        },
        // Alert tier colors
        alert: {
          info: "#3b82f6",     // blue-500
          warning: "#f59e0b",  // amber-500
          urgent: "#f97316",   // orange-500
          emergency: "#dc2626", // red-600
        },
      },
      animation: {
        "pulse-slow": "pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "pulse-fast": "pulse 1s cubic-bezier(0.4, 0, 0.6, 1) infinite",
      },
    },
  },
  plugins: [],
};

export default config;
