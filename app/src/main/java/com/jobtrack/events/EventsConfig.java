package com.jobtrack.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
class EventsConfig {

	@Bean
	EventPublisher eventPublisher(@Value("${jobtrack.events.queue.connection-string}") String connectionString,
			@Value("${jobtrack.events.queue.name}") String queueName) {
		return new AzureQueueEventPublisher(connectionString, queueName);
	}

	/**
	 * Runs the publisher every second. Tests turn this off and call the publisher
	 * directly, so they control exactly when events are sent.
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	@ConditionalOnBooleanProperty(name = "jobtrack.events.publisher.enabled", matchIfMissing = true)
	static class PublishingSchedule {

		private final OutboxPublisher publisher;

		PublishingSchedule(OutboxPublisher publisher) {
			this.publisher = publisher;
		}

		@Scheduled(fixedDelayString = "${jobtrack.events.publisher.delay:1s}")
		void publish() {
			this.publisher.publishDue();
		}

	}

}
