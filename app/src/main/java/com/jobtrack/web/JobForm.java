package com.jobtrack.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The "add job" and "edit job" form. A plain class with getters and setters, because
 * Spring binds form fields to it one property at a time.
 */
public class JobForm {

	@NotBlank(message = "Enter a job title")
	@Size(max = 200)
	private String title;

	@NotBlank(message = "Enter a company")
	@Size(max = 200)
	private String company;

	@Size(max = 200)
	private String location;

	@Size(max = 2000)
	private String sourceUrl;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

}
