// Switches for finished features that are kept out of the UI until they are released.

/**
 * Push notifications (the account-menu toggle, the super admin's "send a message" page and the sign-in/out
 * device handling). The backend and the service worker already support it; flip this to `true` to release it.
 */
export const PUSH_UI = false;
