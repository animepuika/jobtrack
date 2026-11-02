package com.jobtrack.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import com.jobtrack.documents.CvService;
import com.jobtrack.documents.CvService.CvFile;
import com.jobtrack.user.JobTrackUser;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Upload and download CV versions. Files are always sent as downloads with the type
 * decided on upload, never shown inline, and only to their owner.
 */
@Controller
@RequestMapping("/cvs")
class CvPageController {

	private final CvService cvVersions;

	private final Clock clock;

	CvPageController(CvService cvVersions, Clock clock) {
		this.cvVersions = cvVersions;
		this.clock = clock;
	}

	@GetMapping
	String list(@AuthenticationPrincipal JobTrackUser user, Model model) {
		model.addAttribute("nav", "none");
		model.addAttribute("cvs", this.cvVersions.list(user.getId()));
		model.addAttribute("zone", this.clock.getZone());
		return "cvs";
	}

	@PostMapping
	String upload(@AuthenticationPrincipal JobTrackUser user, @RequestParam String label,
			@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
		try (InputStream content = file.getInputStream()) {
			this.cvVersions.upload(user.getId(), label, file.getOriginalFilename(), file.getSize(), content);
			redirect.addFlashAttribute("success", "CV uploaded. You can now pick it on an application.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
			redirect.addFlashAttribute("label", label);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return "redirect:/cvs";
	}

	@GetMapping("/{id}/download")
	ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id) {
		CvFile file = this.cvVersions.open(user.getId(), id);
		ContentDisposition disposition = ContentDisposition.attachment()
			.filename(file.version().getOriginalFilename(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.contentType(MediaType.parseMediaType(file.version().getContentType()))
			.contentLength(file.version().getSizeBytes())
			.body(new InputStreamResource(file.content()));
	}

}
