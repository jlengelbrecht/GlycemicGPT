/**
 * Story 6.3: Browser Notification API wrapper.
 *
 * Provides permission management and severity-appropriate
 * OS-level notifications for urgent and emergency alerts.
 */

/**
 * Request permission for browser notifications.
 *
 * @returns The resulting permission state
 */
export async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (!("Notification" in window)) {
    return "denied";
  }

  if (Notification.permission === "granted") {
    return "granted";
  }

  if (Notification.permission !== "denied") {
    return await Notification.requestPermission();
  }

  return Notification.permission;
}

/**
 * Check if browser notifications are supported and permitted.
 */
export function canShowNotifications(): boolean {
  return "Notification" in window && Notification.permission === "granted";
}

/**
 * Show a browser notification for an alert.
 *
 * @param severity - Alert severity level
 * @param message - Alert message body
 */
export function showBrowserNotification(
  severity: string,
  message: string
): void {
  if (!canShowNotifications()) {
    return;
  }

  const titleMap: Record<string, string> = {
    info: "Glucose Info",
    warning: "Glucose Warning",
    urgent: "Urgent Alert",
    emergency: "EMERGENCY ALERT",
  };

  const title = titleMap[severity] ?? "Alert";

  new Notification(title, {
    body: message,
    requireInteraction: severity === "emergency" || severity === "urgent",
    tag: `glucose-alert-${severity}`,
  });
}
