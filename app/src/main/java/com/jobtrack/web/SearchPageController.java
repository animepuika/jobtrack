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
import com.jobtrack.jobsource.JobSearchQuery;
import com.jobtrack.jobsource.JobSearchQuery.Seniority;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;
import com.jobtrack.jobsource.JobSearchResult;
import com.jobtrack.jobsource.JobSource;
import com.jobtrack.jobsource.JobSourceException;
import com.jobtrack.matching.FitScorer;
import com.jobtrack.matching.FitScorer.Fit;
import com.jobtrack.matching.SkillProfileService;
import com.jobtrack.matching.UserSkill;
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

	private final SkillProfileService skills;

	SearchPageController(JobSource jobSource, JobImportService imports, TrackingService tracking,
			SkillProfileService skills) {
		this.jobSource = jobSource;
		this.imports = imports;
		this.tracking = tracking;
		this.skills = skills;
	}

	@GetMapping("/search")
	String search(@AuthenticationPrincipal JobTrackUser user, @RequestParam(required = false) String keywords,
			@RequestParam(required = false) String location, @RequestParam(required = false) Workplace workplace,
			@RequestParam(required = false) Seniority seniority, @RequestParam(required = false) String sort,
			@RequestParam(required = false) List<String> seen, @RequestParam(required = false) List<Integer> per,
			Model model) {
		List<String> userSkills = this.skills.list(user.getId()).stream().map(UserSkill::getName).toList();
		boolean byFit = !userSkills.isEmpty() && !"provider".equals(sort);
		Links links = Links.of(keywords, location, workplace, seniority, "provider".equals(sort) ? sort : null,
				seen, per);
		model.addAttribute("links", links);
		model.addAttribute("nav", "search");
		model.addAttribute("hasSkills", !userSkills.isEmpty());
		model.addAttribute("skillSearch", String.join(" ", userSkills.stream().limit(2).toList()));
		model.addAttribute("byFit", byFit);
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
			JobSearchPage page = this.jobSource
				.search(new JobSearchQuery(keywords, location, workplace, seniority, links.seen()));
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
	record Links(String keywords, String location, Workplace workplace, Seniority seniority, String sort,
			List<String> seen, List<Integer> per) {

		/**
		 * Every page adds its companies to the address, so stop before it gets too long.
		 */
		static final int MAX_PAGES = 15;

		static Links of(String keywords, String location, Workplace workplace, Seniority seniority, String sort,
				List<String> seen, List<Integer> per) {
			List<String> companies = (seen != null) ? seen : List.of();
			List<Integer> counts = (per != null) ? per : List.of();
			boolean consistent = counts.size() < MAX_PAGES
					&& counts.stream().allMatch((count) -> count != null && count > 0)
					&& counts.stream().mapToInt(Integer::intValue).sum() == companies.size();
			return consistent ? new Links(keywords, location, workplace, seniority, sort, companies, counts)
					: new Links(keywords, location, workplace, seniority, sort, List.of(), List.of());
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
			add(query, "keywords", this.keywords);
			add(query, "location", this.location);
			add(query, "workplace", this.workplace);
			add(query, "seniority", this.seniority);
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
