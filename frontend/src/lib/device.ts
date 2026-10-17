/**
 * Browser-side pieces of the emailed sign-in code:
 * - the "trusted device" token per account (skips the code for 30 days),
 * - the pending challenge, so the one-tap link in the email also works from another tab.
 */
const DEVICES_KEY = "khedma.devices";
const CHALLENGE_KEY = "khedma.loginChallenge";

export interface PendingChallenge {
  challengeToken: string;
  email: string;
  expiresAt: string;
}

function read<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* storage unavailable: the code is simply asked every time */
  }
}

export const deviceStore = {
  get(email: string): string | undefined {
    return read<Record<string, string>>(DEVICES_KEY)?.[email.trim().toLowerCase()];
  },
  set(email: string, token: string) {
    const all = read<Record<string, string>>(DEVICES_KEY) ?? {};
    all[email.trim().toLowerCase()] = token;
    write(DEVICES_KEY, all);
  },
  forget(email: string) {
    const all = read<Record<string, string>>(DEVICES_KEY) ?? {};
    delete all[email.trim().toLowerCase()];
    write(DEVICES_KEY, all);
  },
};

export const challengeStore = {
  get(email?: string): PendingChallenge | null {
    const challenge = read<PendingChallenge>(CHALLENGE_KEY);
    if (!challenge || new Date(challenge.expiresAt).getTime() < Date.now()) return null;
    if (email && challenge.email !== email.trim().toLowerCase()) return null;
    return challenge;
  },
  set(challenge: PendingChallenge | null) {
    write(CHALLENGE_KEY, challenge);
  },
};

export type CodeFlow = "verify" | "reset" | "login";

export interface CodeLink {
  flow: CodeFlow;
  email: string;
  code: string;
}

/**
 * Reads `#otp=123456&flow=login&email=...` from the email's one-tap link and removes it from the
 * address bar right away, so the code doesn't stay in history or get shared by accident.
 */
export function takeCodeLink(): CodeLink | null {
  if (!window.location.hash.startsWith("#otp=")) return null;
  const params = new URLSearchParams(window.location.hash.slice(1));
  window.history.replaceState(null, "", window.location.pathname + window.location.search);
  const code = params.get("otp") ?? "";
  const flow = params.get("flow");
  const email = (params.get("email") ?? "").trim().toLowerCase();
  if (!/^\d{6}$/.test(code) || !email || (flow !== "verify" && flow !== "reset" && flow !== "login")) return null;
  return { flow, email, code };
}

/** Pulls the 6 digits out of whatever was pasted ("Your code is 123 456", Arabic digits, ...). */
export function extractCode(text: string): string {
  const western = text.replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));
  const runs = western.match(/\d[\d\s-]{4,}\d/g) ?? [];
  for (const run of runs) {
    const digits = run.replace(/\D/g, "");
    if (digits.length === 6) return digits;
  }
  return western.replace(/\D/g, "").slice(0, 6);
}
