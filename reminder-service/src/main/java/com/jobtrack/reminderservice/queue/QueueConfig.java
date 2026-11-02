package com.jobtrack.reminderservice.queue;

import com.jobtrack.reminderservice.reminder.ReminderEventHandler;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Connects to the queue and polls it on a fixed delay. Turned off in tests, which
 * call the consumer directly with a fake queue.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("reminders.queue.enabled")
class QueueConfig {

	@Bean
	EventQueue eventQueue(QueueProperties properties) {
		return new AzureEventQueue(properties);
	}

	@Bean
	QueueConsumer queueConsumer(EventQueue queue, ReminderEventHandler handler, JsonMapper json,
			QueueProperties properties) {
		return new QueueConsumer(queue, handler, json, properties.maxAttempts());
	}

	@Bean
	PollingSchedule pollingSchedule(QueueConsumer consumer) {
		return new PollingSchedule(consumer);
	}

	static class PollingSchedule {

		private final QueueConsumer consumer;

		PollingSchedule(QueueConsumer consumer) {
			this.consumer = consumer;
		}

		@Scheduled(fixedDelayString = "${reminders.queue.poll-delay:1s}")
		void poll() {
			this.consumer.pollOnce();
		}

	}

}
