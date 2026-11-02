package com.jobtrack.reminderservice.queue;

import com.jobtrack.reminderservice.queue.EventQueue.QueueItem;
import com.jobtrack.reminderservice.reminder.ReminderEvent;
import com.jobtrack.reminderservice.reminder.ReminderEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Takes messages off the queue and applies them. A message is deleted only after its
 * effect is committed. If handling fails, the message is left alone and the queue
 * delivers it again after its visibility timeout; after too many attempts it goes to
 * the poison queue.
 */
public class QueueConsumer {

	private static final Logger logger = LoggerFactory.getLogger(QueueConsumer.class);

	private static final int BATCH = 16;

	private final EventQueue queue;

	private final ReminderEventHandler handler;

	private final JsonMapper json;

	private final int maxAttempts;

	public QueueConsumer(EventQueue queue, ReminderEventHandler handler, JsonMapper json, int maxAttempts) {
		this.queue = queue;
		this.handler = handler;
		this.json = json;
		this.maxAttempts = maxAttempts;
	}

	/**
	 * Handles one batch. Returns how many messages were taken off the queue.
	 */
	public int pollOnce() {
		int done = 0;
		for (QueueItem item : this.queue.receive(BATCH)) {
			try {
				ReminderEvent event = this.json.readValue(item.body(), ReminderEvent.class);
				this.handler.handle(event);
				this.queue.delete(item);
				done++;
			}
			catch (JacksonException | IllegalArgumentException ex) {
				logger.warn("Message {} cannot be applied, moving it to the poison queue: {}", item.messageId(),
						ex.getMessage());
				this.queue.moveToPoison(item, ex.getMessage());
				done++;
			}
			catch (RuntimeException ex) {
				if (item.dequeueCount() >= this.maxAttempts) {
					logger.error("Message {} failed {} times, moving it to the poison queue", item.messageId(),
							item.dequeueCount(), ex);
					this.queue.moveToPoison(item, ex.getMessage());
					done++;
				}
				else {
					logger.warn("Message {} failed (attempt {}), it will be delivered again: {}", item.messageId(),
							item.dequeueCount(), ex.getMessage());
				}
			}
		}
		return done;
	}

}
