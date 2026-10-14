package com.khedma.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no VAPID keys are set (local development): nothing is sent, the message is only logged. */
public class NoopPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(NoopPushGateway.class);

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Outcome send(Target target, String payload) {
        log.info("Push is not configured (VAPID_* not set); would have sent: {}", payload);
        return Outcome.FAILED;
    }
}
