package com.jobtrack.api;

import java.util.List;
import java.util.Map;

import com.jobtrack.api.ApiRequests.ImportRequest;
import com.jobtrack.jobsource.JobImportService;
import com.jobtrack.jobsource.JobImportService.ImportResult;
import com.jobtrack.jobsource.JobSearchPage;
import com.jobtrack.jobsource.JobSearchQuery;
import com.jobtrack.jobsource.JobSearchQuery.Seniority;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;
import com.jobtrack.jobsource.JobSearchResult;
import com.jobtrack.jobsource.JobSource;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JobSourceApiController {

	private final JobSource jobSource;

	private final JobImportService imports;

	private final TrackingService tracking;

	JobSourceApiController(JobSource jobSource, JobImportService imports, TrackingService tracking) {
		this.jobSource = jobSource;
		this.imports = imports;
		this.tracking = tracking;
	}

	@GetMapping("/api/sources/hiringcafe/jobs")
	SearchResponse search(@AuthenticationPrincipal JobTrackUser user, @RequestParam String keywords,
			@RequestParam(required = false) String location, @RequestParam(required = false) Workplace workplace,
			@RequestParam(required = false) Seniority seniority) {
		JobSearchPage page = this.jobSource.search(new JobSearchQuery(keywords, location, workplace, seniority));
		Map<String, Long> tracked = this.tracking.findTrackedApplicationIds(user.getId(), this.jobSource.provider(),
				page.results().stream().map(JobSearchResult::externalId).toList());
		List<SearchResultResponse> results = page.results()
			.stream()
			.map((result) -> SearchResultResponse.from(result, tracked.get(result.externalId())))
			.toList();
		return new SearchResponse(results, page.total());
	}

	@PostMapping("/api/imports/hiringcafe")
	ResponseEntity<ImportResult> importJob(@AuthenticationPrincipal JobTrackUser user,
			@Valid @RequestBody ImportRequest request) {
		ImportResult result = this.imports.importJob(user.getId(), new JobSearchResult(request.externalId(),
				request.title(), request.company(), request.location(), List.of(), request.url()));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
	}

	record SearchResponse(List<SearchResultResponse> results, int total) {
	}

	record SearchResultResponse(String externalId, String title, String company, String location,
			List<String> highlights, String url, Long trackedApplicationId) {

		static SearchResultResponse from(JobSearchResult result, Long trackedApplicationId) {
			return new SearchResultResponse(result.externalId(), result.title(), result.company(), result.location(),
					result.highlights(), result.url(), trackedApplicationId);
		}

	}

}
