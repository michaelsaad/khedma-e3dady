package com.khedma.push;

import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.khedma.config.AppProperties;

@Configuration
@EnableScheduling
class PushConfig {

    /** The real sender when VAPID keys are set, otherwise one that only logs (local development). */
    @Bean
    PushGateway pushGateway(AppProperties properties, Clock clock) throws GeneralSecurityException {
        AppProperties.Push push = properties.push();
        if (!push.enabled()) return new NoopPushGateway();
        return new WebPushGateway(push.publicKey(), push.privateKey(), push.subject(), clock);
    }

    /** A few daemon threads: enough to fan out to a small group without ever blocking a request. */
    @Bean
    PushExecutor pushExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "push-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        return pool::execute;
    }
}
