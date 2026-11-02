package com.jobtrack;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the queue and the reminder service with one in-memory stand-in, so tests
 * run without Azurite or a second application.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestRemindersConfiguration {

	@Bean
	@Primary
	InMemoryReminderService inMemoryReminderService() {
		return new InMemoryReminderService();
	}

}
