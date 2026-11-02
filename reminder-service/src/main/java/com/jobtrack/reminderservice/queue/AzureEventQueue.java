package com.jobtrack.reminderservice.queue;

import java.time.Duration;
import java.util.List;

import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;

/**
 * Azure Storage Queue, or Azurite locally. Both queues are created on start if they
 * do not exist yet.
 */
public class AzureEventQueue implements EventQueue {

	private final QueueClient queue;

	private final QueueClient poison;

	private final QueueProperties properties;

	public AzureEventQueue(QueueProperties properties) {
		this.properties = properties;
		// One short try per call: the polling loop already retries every second, so
		// the SDK's own retries would only make an outage block the poller longer.
		RequestRetryOptions oneTry = new RequestRetryOptions(RetryPolicyType.FIXED, 1, Duration.ofSeconds(10), null,
				null, null);
		this.queue = new QueueClientBuilder().connectionString(properties.connectionString())
			.queueName(properties.name())
			.retryOptions(oneTry)
			.buildClient();
		this.poison = new QueueClientBuilder().connectionString(properties.connectionString())
			.queueName(properties.poisonName())
			.retryOptions(oneTry)
			.buildClient();
		this.queue.createIfNotExists();
		this.poison.createIfNotExists();
	}

	@Override
	public List<QueueItem> receive(int maxMessages) {
		return this.queue.receiveMessages(maxMessages, this.properties.visibilityTimeout(), null, null)
			.stream()
			.map(AzureEventQueue::toItem)
			.toList();
	}

	@Override
	public void delete(QueueItem item) {
		this.queue.deleteMessage(item.messageId(), item.popReceipt());
	}

	@Override
	public void moveToPoison(QueueItem item, String reason) {
		this.poison.sendMessage(item.body());
		delete(item);
	}

	private static QueueItem toItem(QueueMessageItem message) {
		return new QueueItem(message.getMessageId(), message.getPopReceipt(), message.getBody().toString(),
				message.getDequeueCount());
	}

}
