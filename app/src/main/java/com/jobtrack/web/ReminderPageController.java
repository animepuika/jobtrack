package com.jobtrack.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.regex.Pattern;

import com.jobtrack.reminders.ReminderCommands;
import com.jobtrack.user.JobTrackUser;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Adds, completes and moves reminders. The reminder service applies each change a
 * moment later, so the messages say so. Forms can be on the dashboard or an
 * application page, so each says where to go back to; only those two kinds of local
 * page are accepted, so the redirect can't send anyone to another site.
 */
@Controller
class ReminderPageController {

	private static final Pattern RETURN_TO = Pattern.compile("/(applications/\\d+)?");

	private final ReminderCommands reminders;

	ReminderPageController(ReminderCommands reminders) {
		this.reminders = reminders;
	}

	@PostMapping("/applications/{applicationId}/reminders")
	String add(@AuthenticationPrincipal JobTrackUser user, @PathVariable long applicationId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
			@RequestParam String note, RedirectAttributes redirect) {
		try {
			this.reminders.add(user.getId(), applicationId, date, time, note);
			redirect.addFlashAttribute("success",
					"Reminder set. It appears here and on your dashboard in a moment, until you mark it done.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/applications/" + applicationId;
	}

	@PostMapping("/reminders/{id}/complete")
	String complete(@AuthenticationPrincipal JobTrackUser user, @PathVariable UUID id,
			@RequestParam(defaultValue = "/") String returnTo, RedirectAttributes redirect) {
		this.reminders.complete(user.getId(), id);
		redirect.addFlashAttribute("success", "Reminder done.");
		return "redirect:" + safe(returnTo);
	}

	@PostMapping("/reminders/{id}/reschedule")
	String reschedule(@AuthenticationPrincipal JobTrackUser user, @PathVariable UUID id,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
			@RequestParam(defaultValue = "/") String returnTo, RedirectAttributes redirect) {
		try {
			this.reminders.reschedule(user.getId(), id, date, time);
			redirect.addFlashAttribute("success", "Reminder moved. The new time shows in a moment.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:" + safe(returnTo);
	}

	private static String safe(String returnTo) {
		return RETURN_TO.matcher(returnTo).matches() ? returnTo : "/";
	}

}
