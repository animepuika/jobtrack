package com.jobtrack.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

/**
 * Turns dates into words like "3 days ago" for the pages, in the configured time
 * zone. Templates call it as {@code ${@ages.ago(date)}}.
 */
@Component("ages")
public class Ages {

	private final Clock clock;

	Ages(Clock clock) {
		this.clock = clock;
	}

	/**
	 * "today", "yesterday", "5 days ago", "3 weeks ago" or "2 months ago".
	 */
	public String ago(LocalDate date) {
		if (date == null) {
			return null;
		}
		long days = ChronoUnit.DAYS.between(date, LocalDate.now(this.clock));
		if (days <= 0) {
			return "today";
		}
		if (days == 1) {
			return "yesterday";
		}
		if (days < 14) {
			return days + " days ago";
		}
		if (days < 60) {
			return (days / 7) + " weeks ago";
		}
		return (days / 30) + " months ago";
	}

	public String ago(Instant instant) {
		return (instant == null) ? null : ago(LocalDate.ofInstant(instant, this.clock.getZone()));
	}

	/**
	 * Days since the date, for sorting and for "new" markers.
	 */
	public long daysSince(LocalDate date) {
		return (date == null) ? Long.MAX_VALUE : ChronoUnit.DAYS.between(date, LocalDate.now(this.clock));
	}

}
