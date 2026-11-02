package com.jobtrack.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for the whole application. "Today" is decided in the configured time
 * zone, and tests can replace the clock with a fixed one.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	@Bean
	Clock clock(@Value("${jobtrack.time-zone:Europe/Riga}") ZoneId zone) {
		return Clock.system(zone);
	}

}
