package com.khedma.push;

/** Hands one encrypted-on-the-way message to a browser's push service. */
public interface PushGateway {

    /** Where to send: the device's push service URL and its keys, as the browser gave them. */
    record Target(String endpoint, String p256dh, String auth) {}

    enum Outcome {
        DELIVERED,
        /** The push service says this subscription no longer exists (404/410): delete it. */
        GONE,
        FAILED
    }

    /** True when VAPID keys are configured; otherwise sending only logs. */
    boolean enabled();

    /** @param payload the JSON the service worker receives */
    Outcome send(Target target, String payload);
}
