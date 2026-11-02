package com.jobtrack.tracking;

/**
 * Published inside the archiving transaction, so listeners that save their own
 * changes save them in the same transaction.
 */
public record ApplicationArchivedEvent(long ownerId, long applicationId) {

}
