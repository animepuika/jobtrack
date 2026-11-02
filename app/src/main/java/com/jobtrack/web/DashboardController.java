package com.jobtrack.web;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jobtrack.jobsource.JobSource;
import com.jobtrack.reminders.Reminder;
import com.jobtrack.reminders.ReminderService;
import com.jobtrack.tracking.ApplicationFilter;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
class DashboardController {

	static final int PAGE_SIZE = 20;

	private static final int RECENT_ACTIVITY = 8;

	private final TrackingService tracking;

	private static final int UPCOMING_REMINDERS = 6;

	private final JobSource jobSource;

	private final ReminderService reminders;

	private final Clock clock;

	DashboardController(TrackingService tracking, JobSource jobSource, ReminderService reminders, Clock clock) {
		this.tracking = tracking;
		this.jobSource = jobSource;
		this.reminders = reminders;
		this.clock = clock;
	}

	@GetMapping("/")
	String dashboard(@AuthenticationPrincipal JobTrackUser user, @RequestParam(required = false) Stage stage,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean archived,
			@RequestParam(defaultValue = "1") int page, Model model) {
		model.addAttribute("nav", "dashboard");
		model.addAttribute("searchEnabled", this.jobSource.isEnabled());
		if (this.tracking.countAll(user.getId()) == 0) {
			model.addAttribute("welcome", true);
			model.addAttribute("pathStages",
					List.of(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER, Stage.ACCEPTED));
			model.addAttribute("withdrawn", Stage.WITHDRAWN);
			model.addAttribute("rejected", Stage.REJECTED);
			return "dashboard";
		}
		String text = (q == null || q.isBlank()) ? null : q.strip();
		Page<JobApplication> result = this.tracking.listPage(user.getId(), new ApplicationFilter(stage, text, archived),
				page - 1, PAGE_SIZE);
		Map<Stage, Long> counts = this.tracking.countByStage(user.getId());
		model.addAttribute("result", result);
		model.addAttribute("applications", result.getContent());
		model.addAttribute("firstRow", result.getNumber() * PAGE_SIZE + 1);
		model.addAttribute("lastRow", result.getNumber() * PAGE_SIZE + result.getNumberOfElements());
		model.addAttribute("counts", counts);
		model.addAttribute("openTotal", counts.values().stream().mapToLong(Long::longValue).sum());
		model.addAttribute("recent", this.tracking.recentActivity(user.getId(), RECENT_ACTIVITY));
		model.addAttribute("stages", Stage.values());
		model.addAttribute("selectedStage", stage);
		model.addAttribute("q", text);
		model.addAttribute("archived", archived);
		model.addAttribute("filtered", stage != null || text != null || archived);
		model.addAttribute("links", new Links(stage, text, archived));
		model.addAttribute("zone", this.clock.getZone());
		List<Reminder> upcoming = this.reminders.upcoming(user.getId(), UPCOMING_REMINDERS);
		model.addAttribute("upcoming", upcoming);
		model.addAttribute("reminderApplications", this.tracking.findOwnedByIds(user.getId(),
				upcoming.stream().map(Reminder::getApplicationId).collect(Collectors.toSet())));
		model.addAttribute("now", this.reminders.now());
		return "dashboard";
	}

	/**
	 * Builds dashboard links that keep the current filters. Picking a stage or a page
	 * changes only that part; changing a stage starts again at page 1.
	 */
	record Links(Stage stage, String q, boolean archived) {

		public String stage(Stage newStage) {
			return build(newStage, null);
		}

		public String page(int number) {
			return build(this.stage, number);
		}

		private String build(Stage newStage, Integer page) {
			UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/");
			if (this.q != null) {
				uri.queryParam("q", this.q);
			}
			if (newStage != null) {
				uri.queryParam("stage", newStage.name());
			}
			if (this.archived) {
				uri.queryParam("archived", true);
			}
			if (page != null && page > 1) {
				uri.queryParam("page", page);
			}
			return uri.encode().build().toUriString();
		}

	}

}
