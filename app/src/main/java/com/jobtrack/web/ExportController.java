package com.jobtrack.web;

import java.time.LocalDate;
import java.time.ZoneOffset;

import com.jobtrack.export.ExportService;
import com.jobtrack.export.PersonalExport;
import com.jobtrack.user.JobTrackUser;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloads all of the signed-in user's records as one JSON file.
 */
@RestController
class ExportController {

	private final ExportService exports;

	ExportController(ExportService exports) {
		this.exports = exports;
	}

	@GetMapping({ "/export", "/api/export" })
	ResponseEntity<PersonalExport> export(@AuthenticationPrincipal JobTrackUser user) {
		PersonalExport export = this.exports.export(user.getId(), user.getUsername());
		String filename = "jobtrack-export-" + LocalDate.ofInstant(export.exportedAt(), ZoneOffset.UTC) + ".json";
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body(export);
	}

}
