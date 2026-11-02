package com.jobtrack.web;

import java.time.Clock;

import com.jobtrack.jobsource.JobSource;
import com.jobtrack.tracking.ApplicationFilter;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class DashboardController {

	private static final int RECENT_ACTIVITY = 8;

	private final TrackingService tracking;

	private final JobSource jobSource;

	private final Clock clock;

	DashboardController(TrackingService tracking, JobSource jobSource, Clock clock) {
		this.tracking = tracking;
		this.jobSource = jobSource;
		this.clock = clock;
	}

	@GetMapping("/")
	String dashboard(@AuthenticationPrincipal JobTrackUser user, @RequestParam(required = false) Stage stage,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean archived,
			Model model) {
		model.addAttribute("applications", this.tracking.list(user.getId(), new ApplicationFilter(stage, q, archived)));
		model.addAttribute("counts", this.tracking.countByStage(user.getId()));
		model.addAttribute("recent", this.tracking.recentActivity(user.getId(), RECENT_ACTIVITY));
		model.addAttribute("stages", Stage.values());
		model.addAttribute("selectedStage", stage);
		model.addAttribute("q", q);
		model.addAttribute("archived", archived);
		model.addAttribute("searchEnabled", this.jobSource.isEnabled());
		model.addAttribute("zone", this.clock.getZone());
		return "dashboard";
	}

}
