package com.jobtrack.reminderservice.queue;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param connectionString Azure Storage connection string; locally, Azurite's
 * documented development account
 * @param visibilityTimeout how long a received message stays hidden before it is
 * delivered again if this service did not delete it
 * @param maxAttempts deliveries before a message is moved to the poison queue
 */
@ConfigurationProperties("reminders.queue")
public record QueueProperties(boolean enabled, String connectionString,
		@DefaultValue("reminder-events") String name, @DefaultValue("reminder-events-poison") String poisonName,
		@DefaultValue("30s") Duration visibilityTimeout, @DefaultValue("5") int maxAttempts) {

}
