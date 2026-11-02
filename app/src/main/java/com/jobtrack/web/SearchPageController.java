package com.jobtrack.web;

import java.util.List;
import java.util.Map;

import com.jobtrack.jobsource.JobImportService;
import com.jobtrack.jobsource.JobImportService.ImportResult;
import com.jobtrack.jobsource.JobSearchPage;
import com.jobtrack.jobsource.JobSearchQuery;
import com.jobtrack.jobsource.JobSearchQuery.Seniority;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;
import com.jobtrack.jobsource.JobSearchResult;
import com.jobtrack.jobsource.JobSource;
import com.jobtrack.jobsource.JobSourceException;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Searches HiringCafe on request and saves the results the user picks. Results are
 * not stored until the user chooses one.
 */
@Controller
class SearchPageController {

	private final JobSource jobSource;

	private final JobImportService imports;

	private final TrackingService tracking;

	SearchPageController(JobSource jobSource, JobImportService imports, TrackingService tracking) {
		this.jobSource = jobSource;
		this.imports = imports;
		this.tracking = tracking;
	}

	@GetMapping("/search")
	String search(@AuthenticationPrincipal JobTrackUser user, @RequestParam(required = false) String keywords,
			@RequestParam(required = false) String location, @RequestParam(required = false) Workplace workplace,
			@RequestParam(required = false) Seniority seniority, Model model) {
		model.addAttribute("nav", "search");
		model.addAttribute("keywords", keywords);
		model.addAttribute("location", location);
		model.addAttribute("workplace", workplace);
		model.addAttribute("seniority", seniority);
		model.addAttribute("workplaces", Workplace.values());
		model.addAttribute("seniorities", Seniority.values());
		model.addAttribute("searchEnabled", this.jobSource.isEnabled());
		if (keywords == null || keywords.isBlank() || !this.jobSource.isEnabled()) {
			return "search";
		}
		try {
			JobSearchPage page = this.jobSource.search(new JobSearchQuery(keywords, location, workplace, seniority));
			Map<String, Long> tracked = this.tracking.findTrackedApplicationIds(user.getId(),
					this.jobSource.provider(), page.results().stream().map(JobSearchResult::externalId).toList());
			model.addAttribute("page", page);
			model.addAttribute("tracked", tracked);
		}
		catch (JobSourceException ex) {
			model.addAttribute("searchError", ex.getMessage());
			if (ex.getRetryAfter() != null) {
				model.addAttribute("retryAfterSeconds", ex.getRetryAfter().toSeconds());
			}
		}
		catch (IllegalArgumentException ex) {
			model.addAttribute("searchError", ex.getMessage());
		}
		return "search";
	}

	@PostMapping("/search/track")
	String track(@AuthenticationPrincipal JobTrackUser user, @RequestParam String externalId,
			@RequestParam String title, @RequestParam String company,
			@RequestParam(required = false) String location, @RequestParam(required = false) String url,
			RedirectAttributes redirect) {
		try {
			ImportResult result = this.imports.importJob(user.getId(),
					new JobSearchResult(externalId, title, company, location, List.of(), url));
			redirect.addFlashAttribute("success",
					result.created() ? "Saved from HiringCafe. It starts in the Saved stage." : "You already track this job.");
			return "redirect:/applications/" + result.applicationId();
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", "Could not save that job: " + ex.getMessage());
			return "redirect:/search";
		}
	}

}
