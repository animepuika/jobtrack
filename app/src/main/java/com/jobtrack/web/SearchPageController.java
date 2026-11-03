package com.jobtrack.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.jobtrack.jobsource.JobImportService;
import com.jobtrack.jobsource.JobImportService.ImportResult;
import com.jobtrack.jobsource.JobSearchPage;
import com.jobtrack.jobsource.JobSearchResult;
import com.jobtrack.jobsource.JobSource;
import com.jobtrack.jobsource.JobSourceException;
import com.jobtrack.jobsource.SavedSearchService;
import com.jobtrack.matching.FitScorer;
import com.jobtrack.matching.FitScorer.Fit;
import com.jobtrack.matching.SkillProfileService;
import com.jobtrack.matching.UserSkill;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

	private final SkillProfileService skills;

	private final SavedSearchService savedSearches;

	SearchPageController(JobSource jobSource, JobImportService imports, TrackingService tracking,
			SkillProfileService skills, SavedSearchService savedSearches) {
		this.jobSource = jobSource;
		this.imports = imports;
		this.tracking = tracking;
		this.skills = skills;
		this.savedSearches = savedSearches;
	}

	@GetMapping("/search")
	String search(@AuthenticationPrincipal JobTrackUser user, @ModelAttribute("form") SearchForm form,
			BindingResult binding, @RequestParam(required = false) String sort,
			@RequestParam(required = false) List<String> seen, @RequestParam(required = false) List<Integer> per,
			Model model) {
		List<String> userSkills = this.skills.list(user.getId()).stream().map(UserSkill::getName).toList();
		boolean byFit = !userSkills.isEmpty() && !"provider".equals(sort);
		Links links = Links.of(form.toQueryString(), "provider".equals(sort) ? sort : null, seen, per);
		model.addAttribute("links", links);
		model.addAttribute("nav", "search");
		model.addAttribute("hasSkills", !userSkills.isEmpty());
		model.addAttribute("skillSearch", String.join(" ", userSkills.stream().limit(2).toList()));
		model.addAttribute("byFit", byFit);
		model.addAttribute("options", SearchOptions.INSTANCE);
		model.addAttribute("savedSearches", this.savedSearches.list(user.getId()));
		model.addAttribute("searchEnabled", this.jobSource.isEnabled());
		if (binding.hasErrors()) {
			model.addAttribute("searchError", "Some filters weren't understood: "
					+ String.join(", ", binding.getFieldErrors().stream().map(FieldError::getField).toList())
					+ ". Check them and search again.");
			return "search";
		}
		if (form.isEmpty() || !this.jobSource.isEnabled()) {
			return "search";
		}
		model.addAttribute("activeFilters", form.describe());
		try {
			JobSearchPage page = this.jobSource.search(form.toQuery(links.seen()));
			List<String> newCompanies = page.results()
				.stream()
				.map(JobSearchResult::company)
				.distinct()
				.filter((company) -> !links.seen().contains(company))
				.toList();
			if (!newCompanies.isEmpty() && page.total() > page.results().size()
					&& links.pageNumber() < Links.MAX_PAGES) {
				model.addAttribute("nextPage", links.next(newCompanies));
			}
			Map<String, Long> tracked = this.tracking.findTrackedApplicationIds(user.getId(),
					this.jobSource.provider(), page.results().stream().map(JobSearchResult::externalId).toList());
			Map<String, Fit> fits = new HashMap<>();
			if (!userSkills.isEmpty()) {
				page.results().forEach((result) -> fits.put(result.externalId(),
						FitScorer.score(result.matchingText(), userSkills)));
			}
			List<JobSearchResult> results = byFit ? page.results()
				.stream()
				.sorted(Comparator.comparing((JobSearchResult result) -> fits.get(result.externalId()).level())
					.thenComparing((result) -> -fits.get(result.externalId()).percent()))
				.toList() : page.results();
			model.addAttribute("page", page);
			model.addAttribute("results", results);
			model.addAttribute("fits", fits);
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

	@PostMapping("/search/saved")
	String saveSearch(@AuthenticationPrincipal JobTrackUser user, @RequestParam String name,
			@RequestParam String query, RedirectAttributes redirect) {
		try {
			boolean replaced = this.savedSearches.save(user.getId(), name, query);
			redirect.addFlashAttribute("success", replaced ? "Updated the saved search “" + name.strip() + "”."
					: "Saved as “" + name.strip() + "”. Run it any time from the top of this page.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return SavedSearchService.isSafeQuery(query) ? "redirect:/search?" + query : "redirect:/search";
	}

	@PostMapping("/search/saved/{id}/delete")
	String deleteSearch(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			RedirectAttributes redirect) {
		this.savedSearches.delete(user.getId(), id);
		redirect.addFlashAttribute("success", "Saved search removed.");
		return "redirect:/search";
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

	/**
	 * Builds search links that keep the current filters. HiringCafe has no page number,
	 * so a page is described by the companies shown on earlier pages ({@code seen}) and
	 * how many companies each earlier page added ({@code per}). That lets Previous drop
	 * exactly the last page's companies.
	 */
	record Links(String formQuery, String sort, List<String> seen, List<Integer> per) {

		/**
		 * Every page adds its companies to the address, so stop before it gets too long.
		 */
		static final int MAX_PAGES = 15;

		static Links of(String formQuery, String sort, List<String> seen, List<Integer> per) {
			List<String> companies = (seen != null) ? seen : List.of();
			List<Integer> counts = (per != null) ? per : List.of();
			boolean consistent = counts.size() < MAX_PAGES
					&& counts.stream().allMatch((count) -> count != null && count > 0)
					&& counts.stream().mapToInt(Integer::intValue).sum() == companies.size();
			return consistent ? new Links(formQuery, sort, companies, counts)
					: new Links(formQuery, sort, List.of(), List.of());
		}

		public int pageNumber() {
			return this.per.size() + 1;
		}

		public String sorted(String newSort) {
			return build(newSort, this.seen, this.per);
		}

		public String previous() {
			int keep = this.seen.size() - this.per.getLast();
			return build(this.sort, this.seen.subList(0, keep), this.per.subList(0, this.per.size() - 1));
		}

		public String first() {
			return build(this.sort, List.of(), List.of());
		}

		String next(List<String> newCompanies) {
			List<String> companies = new ArrayList<>(this.seen);
			companies.addAll(newCompanies);
			List<Integer> counts = new ArrayList<>(this.per);
			counts.add(newCompanies.size());
			return build(this.sort, companies, counts);
		}

		private String build(String newSort, List<String> companies, List<Integer> counts) {
			StringJoiner query = new StringJoiner("&", "/search?", "");
			if (!this.formQuery.isEmpty()) {
				query.add(this.formQuery);
			}
			add(query, "sort", newSort);
			companies.forEach((company) -> add(query, "seen", company));
			counts.forEach((count) -> add(query, "per", count));
			return query.toString();
		}

		/**
		 * Encodes strictly: company names can contain "&" or "+".
		 */
		private static void add(StringJoiner query, String name, Object value) {
			if (value != null && !value.toString().isBlank()) {
				query.add(name + "=" + URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
			}
		}

	}

}
