import { api } from "./api";
import type { PushConfig } from "./types";

/** What the notification toggle should show on this device. */
export type PushState =
  | "unsupported" // this browser can't do web push at all
  | "unavailable" // the server has no push keys yet
  | "needs-install" // iPhone/iPad: only works once the site is added to the Home Screen
  | "blocked" // the user (or the browser) said no; only browser settings can undo it
  | "off"
  | "on";

/** Thrown by {@link enablePush} when the user declines the browser's permission prompt. */
export class PushPermissionDenied extends Error {}

const READY_TIMEOUT_MS = 3000;
// Remembered per account on this device, so signing in as someone else never turns their notifications on silently.
const flagKey = (userId: number) => `push-enabled:${userId}`;

const isIOS = () => /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
const isStandalone = () => window.matchMedia("(display-mode: standalone)").matches || (navigator as { standalone?: boolean }).standalone === true;

function remembered(userId: number) {
  try {
    return localStorage.getItem(flagKey(userId)) === "1";
  } catch {
    return false;
  }
}

function remember(userId: number, on: boolean) {
  try {
    if (on) localStorage.setItem(flagKey(userId), "1");
    else localStorage.removeItem(flagKey(userId));
  } catch {
    // storage unavailable (private mode): the toggle still works, it just isn't remembered
  }
}

/** The service worker is only registered in production builds, so give up instead of waiting forever. */
async function registration(): Promise<ServiceWorkerRegistration | null> {
  if (!("serviceWorker" in navigator)) return null;
  return Promise.race([
    navigator.serviceWorker.ready,
    new Promise<null>((resolve) => setTimeout(() => resolve(null), READY_TIMEOUT_MS)),
  ]);
}

function applicationServerKey(publicKey: string) {
  const padded = publicKey.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(publicKey.length / 4) * 4, "=");
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
}

function sameKey(subscription: PushSubscription, key: Uint8Array) {
  const current = subscription.options.applicationServerKey;
  if (!current) return false;
  const bytes = new Uint8Array(current);
  return bytes.length === key.length && bytes.every((value, index) => value === key[index]);
}

export async function pushState(userId: number): Promise<PushState> {
  if (isIOS() && !isStandalone()) return "needs-install";
  if (!("Notification" in window) || !("PushManager" in window)) return "unsupported";
  const config = await api.get<PushConfig>("/push/config").catch(() => null);
  if (!config?.enabled) return "unavailable";
  if (Notification.permission === "denied") return "blocked";
  const reg = await registration();
  if (!reg) return "unsupported";
  const subscription = await reg.pushManager.getSubscription();
  return subscription && Notification.permission === "granted" && remembered(userId) ? "on" : "off";
}

/** Must be called from a tap: browsers only show the permission prompt in response to a user gesture. */
export async function enablePush(userId: number) {
  const config = await api.get<PushConfig>("/push/config");
  const reg = await registration();
  if (!config.enabled || !config.publicKey || !reg) throw new Error("push unavailable");
  if ((await Notification.requestPermission()) !== "granted") throw new PushPermissionDenied();

  const key = applicationServerKey(config.publicKey);
  let subscription = await reg.pushManager.getSubscription();
  if (subscription && !sameKey(subscription, key)) {
    // Made for an older server key (the keys were replaced): it can never receive from this server.
    await subscription.unsubscribe();
    subscription = null;
  }
  if (!subscription) {
    subscription = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  }
  await send(subscription);
  remember(userId, true);
}

export async function disablePush(userId: number) {
  const reg = await registration();
  const subscription = await reg?.pushManager.getSubscription();
  if (subscription) {
    await api.delete(`/push/subscribe?endpoint=${encodeURIComponent(subscription.endpoint)}`).catch(() => undefined);
    await subscription.unsubscribe();
  }
  remember(userId, false);
}

/**
 * After signing in: if this account turned notifications on here before, point the device at it again
 * (a device always belongs to whoever is signed in). Never prompts.
 */
export async function resyncPush(userId: number) {
  if (!remembered(userId) || !("Notification" in window) || Notification.permission !== "granted") return;
  const reg = await registration();
  const subscription = await reg?.pushManager.getSubscription();
  if (subscription) await send(subscription).catch(() => undefined);
}

/** On sign-out: this device stops receiving the previous account's notifications (the browser keeps the subscription). */
export async function detachPush() {
  try {
    const reg = await registration();
    const subscription = await reg?.pushManager.getSubscription();
    if (subscription) await api.delete(`/push/subscribe?endpoint=${encodeURIComponent(subscription.endpoint)}`).catch(() => undefined);
  } catch {
    // signing out must never fail because of notifications
  }
}

function send(subscription: PushSubscription) {
  const json = subscription.toJSON();
  return api.post("/push/subscribe", { endpoint: json.endpoint, keys: json.keys });
}
