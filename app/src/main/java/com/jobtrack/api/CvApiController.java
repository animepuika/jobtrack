package com.jobtrack.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.jobtrack.documents.CvService;
import com.jobtrack.documents.CvService.CvFile;
import com.jobtrack.documents.CvVersion;
import com.jobtrack.user.JobTrackUser;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cv-versions")
class CvApiController {

	private final CvService cvVersions;

	CvApiController(CvService cvVersions) {
		this.cvVersions = cvVersions;
	}

	@GetMapping
	List<CvResponse> list(@AuthenticationPrincipal JobTrackUser user) {
		return this.cvVersions.list(user.getId()).stream().map(CvResponse::from).toList();
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<CvResponse> upload(@AuthenticationPrincipal JobTrackUser user, @RequestParam String label,
			@RequestParam("file") MultipartFile file) throws IOException {
		try (InputStream content = file.getInputStream()) {
			CvVersion cv = this.cvVersions.upload(user.getId(), label, file.getOriginalFilename(), file.getSize(),
					content);
			return ResponseEntity.created(URI.create("/api/cv-versions/" + cv.getId())).body(CvResponse.from(cv));
		}
	}

	@GetMapping("/{id}/file")
	ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		CvFile file = this.cvVersions.open(user.getId(), id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(file.version().getOriginalFilename(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.contentType(MediaType.parseMediaType(file.version().getContentType()))
			.contentLength(file.version().getSizeBytes())
			.body(new InputStreamResource(file.content()));
	}

	record CvResponse(long id, String label, String originalFilename, String contentType, long sizeBytes,
			Instant uploadedAt) {

		static CvResponse from(CvVersion cv) {
			return new CvResponse(cv.getId(), cv.getLabel(), cv.getOriginalFilename(), cv.getContentType(),
					cv.getSizeBytes(), cv.getUploadedAt());
		}

	}

}
