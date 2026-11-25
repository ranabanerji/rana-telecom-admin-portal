package com.example.telecom.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ClockConfig {
    // SQLite stores timestamps in epoch milliseconds, so a finer clock would make a
    // write's response disagree with every later read of the same row.
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
