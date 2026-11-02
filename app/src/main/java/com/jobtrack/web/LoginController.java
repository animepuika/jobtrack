package com.jobtrack.web;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {

	private final Environment environment;

	LoginController(Environment environment) {
		this.environment = environment;
	}

	@GetMapping("/login")
	String login(Model model) {
		model.addAttribute("demo", this.environment.matchesProfiles("demo"));
		return "login";
	}

}
