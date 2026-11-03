package com.jobtrack.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.jobtrack.documents.CvService;
import com.jobtrack.documents.CvVersion;
import com.jobtrack.reminders.ReminderCommands;
import com.jobtrack.reminders.ReminderReader;
import com.jobtrack.reminders.ReminderView;
import com.jobtrack.reminders.RemindersUnavailableException;
import com.jobtrack.tracking.ApplicationDetails;
import com.jobtrack.tracking.DuplicateJobException;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.SourceUrls;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.StageChangeNotAllowedException;
import com.jobtrack.tracking.TrackedJob;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.JobTrackUser;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pages for adding, viewing and updating one application. Every change is a form
 * POST followed by a redirect, so refreshing the page never repeats the change.
 */
@Controller
@RequestMapping("/applications")
class ApplicationPageController {

	private static final Set<String> OVERVIEW_PAGES = Set.of("/", "/board", "/progress");

	private final TrackingService tracking;

	private final CvService cvVersions;

	private final ReminderReader reminders;

	private final ReminderCommands reminderCommands;

	private final Clock clock;

	ApplicationPageController(TrackingService tracking, CvService cvVersions, ReminderReader reminders,
			ReminderCommands reminderCommands, Clock clock) {
		this.tracking = tracking;
		this.cvVersions = cvVersions;
		this.reminders = reminders;
		this.reminderCommands = reminderCommands;
		this.clock = clock;
	}

	@GetMapping("/new")
	String newJob(Model model) {
		model.addAttribute("jobForm", new JobForm());
		formPage(model, false, null);
		return "application-form";
	}

	@PostMapping
	String create(@AuthenticationPrincipal JobTrackUser user, @Valid @ModelAttribute("jobForm") JobForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		formPage(model, false, null);
		checkLink(form, result);
		if (result.hasErrors()) {
			return "application-form";
		}
		try {
			JobApplication application = this.tracking.saveJob(user.getId(),
					NewJob.manual(form.getTitle(), form.getCompany(), form.getLocation(), form.getSourceUrl()));
			redirect.addFlashAttribute("success", "Job saved. It starts in the Saved stage.");
			return "redirect:/applications/" + application.getId();
		}
		catch (DuplicateJobException ex) {
			ApplicationDetails existing = this.tracking.get(user.getId(), ex.getExistingApplicationId());
			model.addAttribute("duplicate", existing.application());
			model.addAttribute("duplicateSavedOn", StageProgress.savedOn(existing.history()));
			return "application-form";
		}
		catch (IllegalArgumentException ex) {
			result.reject("invalid", ex.getMessage());
			return "application-form";
		}
	}

	@GetMapping("/{id}")
	String show(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id, Model model) {
		ApplicationDetails details = this.tracking.get(user.getId(), id);
		JobApplication application = details.application();
		model.addAttribute("nav", "none");
		model.addAttribute("jobApplication", application);
		model.addAttribute("job", application.getJob());
		model.addAttribute("history", details.history().reversed());
		model.addAttribute("steps", StageProgress.of(application.getStage(), details.history()));
		model.addAttribute("moveHelp", StageProgress.MOVE_HELP);
		model.addAttribute("savedOn", StageProgress.savedOn(details.history()));
		model.addAttribute("updatedOn", LocalDate.ofInstant(application.getUpdatedAt(), this.clock.getZone()));
		model.addAttribute("today", this.tracking.today());
		List<CvVersion> cvs = this.cvVersions.list(user.getId());
		model.addAttribute("cvs", cvs);
		model.addAttribute("currentCv", cvs.stream()
			.filter((cv) -> cv.getId().equals(application.getCvVersionId()))
			.findFirst()
			.orElse(null));
		model.addAttribute("remindersUnavailable", false);
		try {
			List<ReminderView> reminderList = this.reminders.forApplication(user.getId(), id);
			model.addAttribute("openReminders", reminderList.stream().filter((r) -> !r.isCompleted()).toList());
			model.addAttribute("doneReminders", reminderList.stream().filter(ReminderView::isCompleted).toList());
		}
		catch (RemindersUnavailableException ex) {
			model.addAttribute("remindersUnavailable", true);
			model.addAttribute("openReminders", List.of());
			model.addAttribute("doneReminders", List.of());
		}
		model.addAttribute("pendingReminderChanges", this.reminderCommands.pendingChanges(user.getId()));
		model.addAttribute("now", Instant.now(this.clock));
		model.addAttribute("zone", this.clock.getZone());
		return "application-detail";
	}

	@PostMapping("/{id}/cv")
	String useCv(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@RequestParam(required = false) Long cvVersionId, RedirectAttributes redirect) {
		this.tracking.useCvVersion(user.getId(), id, cvVersionId);
		redirect.addFlashAttribute("success", (cvVersionId != null) ? "CV version saved." : "CV version cleared.");
		return "redirect:/applications/" + id;
	}

	@PostMapping("/{id}/stage")
	String changeStage(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id, @RequestParam Stage stage,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
		boolean elsewhere = isOverviewPage(returnTo);
		try {
			JobApplication application = this.tracking.changeStage(user.getId(), id, stage, date);
			redirect.addFlashAttribute("success",
					elsewhere ? "Moved “" + application.getJob().getTitle() + "” to " + stage.getLabel() + "."
							: "Moved to " + stage.getLabel() + ". The change is in the history below.");
		}
		catch (StageChangeNotAllowedException ex) {
			redirect.addFlashAttribute("error", "That move isn't possible: this application is already at "
					+ ex.getFrom().getLabel() + ", and stages only move forward. Nothing was changed.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:" + (elsewhere ? returnTo : "/applications/" + id);
	}

	@PostMapping("/{id}/notes")
	String addNote(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id, @RequestParam String text,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
		boolean elsewhere = isOverviewPage(returnTo);
		try {
			JobApplication application = this.tracking.addNote(user.getId(), id, text, date);
			redirect.addFlashAttribute("success", elsewhere
					? "Noted on “" + application.getJob().getTitle() + "”. The follow-up clock starts again."
					: "Note added.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:" + (elsewhere ? returnTo : "/applications/" + id);
	}

	/**
	 * The overview pages a stage move or note can come from. Only these are redirected
	 * to, so the parameter can't send anyone to another site.
	 */
	private static boolean isOverviewPage(String returnTo) {
		return returnTo != null && OVERVIEW_PAGES.contains(returnTo);
	}

	@GetMapping("/{id}/edit")
	String edit(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id, Model model) {
		TrackedJob job = this.tracking.get(user.getId(), id).application().getJob();
		JobForm form = new JobForm();
		form.setTitle(job.getTitle());
		form.setCompany(job.getCompany());
		form.setLocation(job.getLocation());
		form.setSourceUrl(job.getSourceUrl());
		model.addAttribute("jobForm", form);
		formPage(model, true, id);
		model.addAttribute("source", job.getSourceProvider());
		return "application-form";
	}

	@PostMapping("/{id}/edit")
	String update(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id,
			@Valid @ModelAttribute("jobForm") JobForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		formPage(model, true, id);
		TrackedJob job = this.tracking.get(user.getId(), id).application().getJob();
		model.addAttribute("source", job.getSourceProvider());
		form.setSourceUrl(job.getSourceUrl());
		if (result.hasErrors()) {
			return "application-form";
		}
		try {
			this.tracking.updateJobDetails(user.getId(), id, form.getTitle(), form.getCompany(), form.getLocation());
		}
		catch (IllegalArgumentException ex) {
			result.reject("invalid", ex.getMessage());
			return "application-form";
		}
		redirect.addFlashAttribute("success", "Job details updated.");
		return "redirect:/applications/" + id;
	}

	@PostMapping("/{id}/archive")
	String archive(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id, RedirectAttributes redirect) {
		this.tracking.setArchived(user.getId(), id, true);
		redirect.addFlashAttribute("success",
				"Archived. It's hidden from your dashboard unless you show archived jobs. Nothing is deleted.");
		return "redirect:/applications/" + id;
	}

	@PostMapping("/{id}/restore")
	String restore(@AuthenticationPrincipal JobTrackUser user, @PathVariable long id, RedirectAttributes redirect) {
		this.tracking.setArchived(user.getId(), id, false);
		redirect.addFlashAttribute("success", "Restored. It's back on your dashboard.");
		return "redirect:/applications/" + id;
	}

	private static void formPage(Model model, boolean editing, Long applicationId) {
		model.addAttribute("nav", editing ? "none" : "add");
		model.addAttribute("editing", editing);
		model.addAttribute("applicationId", applicationId);
	}

	private static void checkLink(JobForm form, BindingResult result) {
		if (result.hasFieldErrors("sourceUrl")) {
			return;
		}
		try {
			SourceUrls.normalize(form.getSourceUrl());
		}
		catch (IllegalArgumentException ex) {
			result.rejectValue("sourceUrl", "invalid", "Enter the full link, starting with https://");
		}
	}

}
