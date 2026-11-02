package com.jobtrack.reminderservice.queue;

import java.util.List;

/**
 * The incoming queue. A received message stays invisible for a while; if it is not
 * deleted in time it becomes visible again and is delivered once more.
 */
public interface EventQueue {

	List<QueueItem> receive(int maxMessages);

	void delete(QueueItem item);

	/**
	 * Moves a message that keeps failing to the poison queue, so it stops blocking the
	 * others and can be inspected later.
	 */
	void moveToPoison(QueueItem item, String reason);

	record QueueItem(String messageId, String popReceipt, String body, long dequeueCount) {
	}

}
