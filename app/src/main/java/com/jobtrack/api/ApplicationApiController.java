package com.jobtrack.api;

import java.net.URI;
import java.util.List;

import com.jobtrack.api.ApiRequests.CreateJobRequest;
import com.jobtrack.api.ApiRequests.NoteRequest;
import com.jobtrack.api.ApiRequests.StageChangeRequest;
import com.jobtrack.api.ApiRequests.UpdateJobRequest;
import com.jobtrack.tracking.ApplicationFilter;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tracked jobs and their applications as JSON. A saved job always has exactly one
 * application, so both are created and returned together.
 */
@RestController
@RequestMapping("/api/applications")
class ApplicationApiController {

	private final TrackingService tracking;

	ApplicationApiController(TrackingService tracking) {
		this.tracking = tracking;
	}

	@GetMapping
	List<ApplicationResponse> list(@AuthenticationPrincipal JobTrackUser user,
			@RequestParam(required = false) Stage stage, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean includeArchived) {
		return this.tracking.list(user.getId(), new ApplicationFilter(stage, q, includeArchived))
			.stream()
			.map(ApplicationResponse::from)
			.toList();
	}

	@PostMapping
	ResponseEntity<ApplicationResponse> create(@AuthenticationPrincipal JobTrackUser user,
			@Valid @RequestBody CreateJobRequest request) {
		JobApplication application = this.tracking.saveJob(user.getId(),
				NewJob.manual(request.title(), request.company(), request.location(), request.sourceUrl()));
		return ResponseEntity.created(URI.create("/api/applications/" + application.getId()))
			.body(ApplicationResponse.from(application));
	}

	@GetMapping("/{id}")
	ApplicationDetailsResponse get(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		return ApplicationDetailsResponse.from(this.tracking.get(user.getId(), id));
	}

	@PatchMapping("/{id}")
	ApplicationResponse updateJob(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@Valid @RequestBody UpdateJobRequest request) {
		return ApplicationResponse.from(this.tracking.updateJobDetails(user.getId(), id, request.title(),
				request.company(), request.location()));
	}

	@PatchMapping("/{id}/stage")
	ApplicationResponse changeStage(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@Valid @RequestBody StageChangeRequest request) {
		return ApplicationResponse
			.from(this.tracking.changeStage(user.getId(), id, request.stage(), request.date()));
	}

	@PostMapping("/{id}/notes")
	ResponseEntity<ApplicationDetailsResponse> addNote(@AuthenticationPrincipal JobTrackUser user,
			@PathVariable long id, @Valid @RequestBody NoteRequest request) {
		this.tracking.addNote(user.getId(), id, request.text(), request.date());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApplicationDetailsResponse.from(this.tracking.get(user.getId(), id)));
	}

	@PostMapping("/{id}/archive")
	ApplicationResponse archive(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		return ApplicationResponse.from(this.tracking.setArchived(user.getId(), id, true));
	}

	@PostMapping("/{id}/restore")
	ApplicationResponse restore(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		return ApplicationResponse.from(this.tracking.setArchived(user.getId(), id, false));
	}

}
