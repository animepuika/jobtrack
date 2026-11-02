package com.jobtrack.web;

import java.time.Clock;
import java.util.List;

import com.jobtrack.documents.CvService;
import com.jobtrack.matching.SkillProfileService;
import com.jobtrack.matching.UserSkill;
import com.jobtrack.user.JobTrackUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The skills used to compare search results with the user's CV.
 */
@Controller
@RequestMapping("/skills")
class SkillsPageController {

	private final SkillProfileService skills;

	private final CvService cvVersions;

	private final Clock clock;

	SkillsPageController(SkillProfileService skills, CvService cvVersions, Clock clock) {
		this.skills = skills;
		this.cvVersions = cvVersions;
		this.clock = clock;
	}

	@GetMapping
	String show(@AuthenticationPrincipal JobTrackUser user, Model model) {
		List<UserSkill> list = this.skills.list(user.getId());
		model.addAttribute("nav", "none");
		model.addAttribute("skills", list);
		model.addAttribute("profile", this.skills.profileOf(user.getId()).orElse(null));
		model.addAttribute("cvs", this.cvVersions.list(user.getId()));
		model.addAttribute("zone", this.clock.getZone());
		return "skills";
	}

	@PostMapping("/from-cv")
	String readFromCv(@AuthenticationPrincipal JobTrackUser user, @RequestParam long cvVersionId,
			RedirectAttributes redirect) {
		try {
			List<UserSkill> found = this.skills.readFromCv(user.getId(), cvVersionId);
			redirect.addFlashAttribute("success", found.size() + " skills found. Remove any that don't fit, and add "
					+ "ones the CV doesn't mention.");
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/skills";
	}

	@PostMapping
	String add(@AuthenticationPrincipal JobTrackUser user, @RequestParam String name, RedirectAttributes redirect) {
		try {
			this.skills.add(user.getId(), name);
		}
		catch (IllegalArgumentException ex) {
			redirect.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/skills";
	}

	@PostMapping("/remove")
	String remove(@AuthenticationPrincipal JobTrackUser user, @RequestParam String name) {
		this.skills.remove(user.getId(), name);
		return "redirect:/skills";
	}

}
