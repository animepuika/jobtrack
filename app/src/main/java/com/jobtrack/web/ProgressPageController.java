package com.jobtrack.web;

import java.util.List;

import com.jobtrack.tracking.ProgressService;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The progress page (how the search is going) and the board (every open application
 * by stage).
 */
@Controller
class ProgressPageController {

	private final ProgressService progress;

	private final TrackingService tracking;

	ProgressPageController(ProgressService progress, TrackingService tracking) {
		this.progress = progress;
		this.tracking = tracking;
	}

	@GetMapping("/progress")
	String progress(@AuthenticationPrincipal JobTrackUser user, Model model) {
		model.addAttribute("nav", "progress");
		model.addAttribute("progress", this.progress.progress(user.getId()));
		model.addAttribute("followUps", this.progress.followUps(user.getId()));
		model.addAttribute("followUpDays", ProgressService.FOLLOW_UP_DAYS);
		model.addAttribute("today", this.tracking.today());
		model.addAttribute("outcomeStages", List.of(Stage.ACCEPTED, Stage.REJECTED, Stage.WITHDRAWN));
		return "progress";
	}

	@GetMapping("/board")
	String board(@AuthenticationPrincipal JobTrackUser user, Model model) {
		model.addAttribute("nav", "board");
		model.addAttribute("board", this.progress.board(user.getId()));
		model.addAttribute("followUpDays", ProgressService.FOLLOW_UP_DAYS);
		model.addAttribute("today", this.tracking.today());
		return "board";
	}

}
