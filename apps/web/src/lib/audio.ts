/**
 * Story 6.3: Web Audio API sound generator for alert notifications.
 *
 * Generates programmatic tones with severity-based frequencies
 * and beep patterns. No audio files required.
 * Uses a singleton AudioContext to avoid browser context limits.
 */

type AlertSeverity = "info" | "warning" | "urgent" | "emergency";

interface ToneConfig {
  frequency: number;
  beeps: number;
  beepDuration: number;
  gap: number;
}

const ALERT_TONES: Record<AlertSeverity, ToneConfig> = {
  info: { frequency: 440, beeps: 1, beepDuration: 0.15, gap: 0 },
  warning: { frequency: 550, beeps: 2, beepDuration: 0.15, gap: 0.1 },
  urgent: { frequency: 660, beeps: 3, beepDuration: 0.15, gap: 0.1 },
  emergency: { frequency: 880, beeps: 5, beepDuration: 0.2, gap: 0.08 },
};

let sharedContext: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  try {
    if (!sharedContext || sharedContext.state === "closed") {
      sharedContext = new AudioContext();
    }
    return sharedContext;
  } catch {
    return null;
  }
}

/**
 * Play an alert sound using the Web Audio API.
 *
 * @param severity - Alert severity level determining tone pattern
 */
export async function playAlertSound(severity: string): Promise<void> {
  const config =
    ALERT_TONES[severity as AlertSeverity] ?? ALERT_TONES.info;

  try {
    const audioContext = getAudioContext();
    if (!audioContext) return;

    // Resume if suspended (browser autoplay policy)
    if (audioContext.state === "suspended") {
      await audioContext.resume();
    }

    const now = audioContext.currentTime;

    for (let i = 0; i < config.beeps; i++) {
      const oscillator = audioContext.createOscillator();
      const gainNode = audioContext.createGain();

      oscillator.connect(gainNode);
      gainNode.connect(audioContext.destination);

      oscillator.frequency.value = config.frequency;
      oscillator.type = "sine";

      const startTime = now + i * (config.beepDuration + config.gap);
      const endTime = startTime + config.beepDuration;

      // Smooth gain envelope to prevent clicks
      gainNode.gain.setValueAtTime(0, startTime);
      gainNode.gain.linearRampToValueAtTime(0.3, startTime + 0.01);
      gainNode.gain.linearRampToValueAtTime(0.3, endTime - 0.01);
      gainNode.gain.linearRampToValueAtTime(0, endTime);

      oscillator.start(startTime);
      oscillator.stop(endTime);
    }
  } catch {
    // Silently fail - user may have audio disabled or unsupported browser
  }
}
