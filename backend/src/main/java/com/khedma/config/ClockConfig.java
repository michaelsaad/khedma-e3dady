package com.khedma.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Clock in the service's local timezone (Cairo), so "today" means the local day. */
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(properties.timezone());
    }
}
