package com.jobtrack.events;

import java.time.Duration;

import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;

/**
 * Sends events to Azure Storage Queue, or Azurite locally. The queue is created on
 * the first successful send, so the tracker starts even while the queue is down.
 * <p>
 * The SDK's own retries are turned off: one try with a short timeout, so a failure
 * reaches the outbox quickly and is retried there, where it is visible and does not
 * hold up other work.
 */
class AzureQueueEventPublisher implements EventPublisher {

	private static final RequestRetryOptions ONE_TRY = new RequestRetryOptions(RetryPolicyType.FIXED, 1,
			Duration.ofSeconds(5), null, null, null);

	private final QueueClient queue;

	private volatile boolean queueReady;

	AzureQueueEventPublisher(String connectionString, String queueName) {
		this.queue = new QueueClientBuilder().connectionString(connectionString)
			.queueName(queueName)
			.retryOptions(ONE_TRY)
			.buildClient();
	}

	@Override
	public void send(String json) {
		if (!this.queueReady) {
			this.queue.createIfNotExists();
			this.queueReady = true;
		}
		this.queue.sendMessage(json);
	}

}
