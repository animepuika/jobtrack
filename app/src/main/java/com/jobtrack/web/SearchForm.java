package com.jobtrack.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.jobtrack.jobsource.JobSearchQuery;
import com.jobtrack.jobsource.JobSearchQuery.CompanySize;
import com.jobtrack.jobsource.JobSearchQuery.Department;
import com.jobtrack.jobsource.JobSearchQuery.EmploymentType;
import com.jobtrack.jobsource.JobSearchQuery.Industry;
import com.jobtrack.jobsource.JobSearchQuery.Option;
import com.jobtrack.jobsource.JobSearchQuery.OrganizationType;
import com.jobtrack.jobsource.JobSearchQuery.PayPeriod;
import com.jobtrack.jobsource.JobSearchQuery.Perk;
import com.jobtrack.jobsource.JobSearchQuery.RoleType;
import com.jobtrack.jobsource.JobSearchQuery.Seniority;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;

/**
 * The search form. Spring fills it from the address, so a search is a plain link that
 * can be bookmarked or saved; {@link #toQueryString()} writes it back. Each field name
 * is the URL parameter name.
 */
public class SearchForm {

	private String keywords;

	private String location;

	private Integer distance;

	private Set<Workplace> workplace = EnumSet.noneOf(Workplace.class);

	private Set<Seniority> seniority = EnumSet.noneOf(Seniority.class);

	private Integer posted;

	private Set<EmploymentType> type = EnumSet.noneOf(EmploymentType.class);

	private RoleType role;

	private Integer experience;

	private Integer pay;

	private PayPeriod payPeriod;

	private boolean publishedPay;

	private Set<Department> department = EnumSet.noneOf(Department.class);

	private Set<Industry> industry = EnumSet.noneOf(Industry.class);

	private Set<Industry> skipIndustry = EnumSet.noneOf(Industry.class);

	private CompanySize size;

	private Set<OrganizationType> org = EnumSet.noneOf(OrganizationType.class);

	private boolean noDegree;

	private String languages;

	private String countries;

	private String companies;

	private String hideCompanies;

	private Set<Perk> perk = EnumSet.noneOf(Perk.class);

	private boolean quickApply;

	private Integer perCompany;

	/**
	 * The query for the provider. {@code skip} adds companies shown on earlier pages to
	 * the ones the user chose to hide.
	 */
	public JobSearchQuery toQuery(List<String> skip) {
		List<String> excluded = new ArrayList<>(split(this.hideCompanies));
		excluded.addAll(skip);
		return JobSearchQuery.builder(this.keywords)
			.location(this.location)
			.radiusKm(this.distance)
			.workplaces(this.workplace)
			.seniorities(this.seniority)
			.postedWithinDays(this.posted)
			.employmentTypes(this.type)
			.roleType(this.role)
			.maxYearsExperience(this.experience)
			.minPay(this.pay, this.payPeriod)
			.publishedPayOnly(this.publishedPay)
			.departments(this.department)
			.industries(this.industry)
			.excludedIndustries(this.skipIndustry)
			.companySize(this.size)
			.organizationTypes(this.org)
			.noDegreeRequired(this.noDegree)
			.languages(split(this.languages))
			.companyCountries(split(this.countries))
			.onlyCompanies(split(this.companies))
			.excludedCompanies(excluded)
			.perks(this.perk)
			.quickApplyOnly(this.quickApply)
			.jobsPerCompany(this.perCompany)
			.build();
	}

	/**
	 * True when nothing was entered, so there is nothing to search for yet.
	 */
	public boolean isEmpty() {
		return isBlank(this.keywords) && isBlank(this.location) && moreFilterCount() == 0
				&& this.workplace.isEmpty() && this.seniority.isEmpty() && this.posted == null;
	}

	/**
	 * How many filters inside "More filters" are set, so the panel can say so and open
	 * itself.
	 */
	public int moreFilterCount() {
		return count(this.distance != null, !this.type.isEmpty(), this.role != null, this.experience != null,
				this.pay != null, this.publishedPay, !this.department.isEmpty(), !this.industry.isEmpty(),
				!this.skipIndustry.isEmpty(), this.size != null, !this.org.isEmpty(), this.noDegree,
				!isBlank(this.languages), !isBlank(this.countries), !isBlank(this.companies),
				!isBlank(this.hideCompanies), !this.perk.isEmpty(), this.quickApply, this.perCompany != null);
	}

	/**
	 * The chosen filters in words, for the results heading and saved searches.
	 */
	public List<String> describe() {
		List<String> parts = new ArrayList<>();
		if (!isBlank(this.location)) {
			parts.add((this.distance != null) ? "Within " + this.distance + " km of " + this.location.strip()
					: this.location.strip());
		}
		addLabels(parts, "", this.workplace, " or ");
		addLabels(parts, "", this.seniority, " or ");
		if (this.posted != null) {
			parts.add((this.posted == 1) ? "Posted today" : "Posted in the last " + this.posted + " days");
		}
		addLabels(parts, "", this.type, " or ");
		if (this.role != null) {
			parts.add(this.role.label());
		}
		if (this.experience != null) {
			parts.add("Up to " + this.experience + ((this.experience == 1) ? " year" : " years") + " of experience");
		}
		if (this.pay != null) {
			parts.add("At least $" + String.format("%,d", this.pay) + " "
					+ ((this.payPeriod != null) ? this.payPeriod : PayPeriod.YEARLY).label());
		}
		if (this.publishedPay) {
			parts.add("Pay published");
		}
		addLabels(parts, "", this.department, " or ");
		addLabels(parts, "", this.industry, " or ");
		addLabels(parts, "Not ", this.skipIndustry, ", ");
		if (this.size != null) {
			parts.add("Company: " + this.size.label());
		}
		addLabels(parts, "", this.org, " or ");
		if (this.noDegree) {
			parts.add("No degree required");
		}
		addText(parts, "Languages: ", this.languages);
		addText(parts, "Company based in ", this.countries);
		addText(parts, "Only ", this.companies);
		addText(parts, "Hiding ", this.hideCompanies);
		addLabels(parts, "", this.perk, ", ");
		if (this.quickApply) {
			parts.add("Quick applications");
		}
		if (this.perCompany != null) {
			parts.add("Up to " + this.perCompany + " jobs per company");
		}
		return parts;
	}

	/**
	 * A name to offer when saving: the keywords, then the filters, cut to fit.
	 */
	public String suggestedName() {
		List<String> parts = new ArrayList<>();
		if (!isBlank(this.keywords)) {
			parts.add(this.keywords.strip());
		}
		parts.addAll(describe());
		String name = String.join(", ", parts);
		return (name.length() <= 60) ? name : name.substring(0, 57).strip() + "...";
	}

	/**
	 * The form as URL parameters, in a fixed order and strictly encoded. Paging and
	 * sorting are added by the caller.
	 */
	public String toQueryString() {
		StringJoiner query = new StringJoiner("&");
		add(query, "keywords", this.keywords);
		add(query, "location", this.location);
		add(query, "distance", this.distance);
		addAll(query, "workplace", this.workplace);
		addAll(query, "seniority", this.seniority);
		add(query, "posted", this.posted);
		addAll(query, "type", this.type);
		add(query, "role", this.role);
		add(query, "experience", this.experience);
		add(query, "pay", this.pay);
		if (this.pay != null) {
			add(query, "payPeriod", this.payPeriod);
		}
		add(query, "publishedPay", this.publishedPay ? "true" : null);
		addAll(query, "department", this.department);
		addAll(query, "industry", this.industry);
		addAll(query, "skipIndustry", this.skipIndustry);
		add(query, "size", this.size);
		addAll(query, "org", this.org);
		add(query, "noDegree", this.noDegree ? "true" : null);
		add(query, "languages", this.languages);
		add(query, "countries", this.countries);
		add(query, "companies", this.companies);
		add(query, "hideCompanies", this.hideCompanies);
		addAll(query, "perk", this.perk);
		add(query, "quickApply", this.quickApply ? "true" : null);
		add(query, "perCompany", this.perCompany);
		return query.toString();
	}

	static List<String> split(String text) {
		if (isBlank(text)) {
			return List.of();
		}
		return Arrays.stream(text.split(",")).map(String::strip).filter((part) -> !part.isEmpty()).toList();
	}

	private static void add(StringJoiner query, String name, Object value) {
		if (value != null && !value.toString().isBlank()) {
			String text = (value instanceof Enum<?> constant) ? constant.name() : value.toString().strip();
			query.add(name + "=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
		}
	}

	private static void addAll(StringJoiner query, String name, Collection<? extends Enum<?>> values) {
		values.stream().filter(Objects::nonNull).forEach((value) -> add(query, name, value));
	}

	private static void addLabels(List<String> parts, String prefix, Collection<? extends Option> options,
			String separator) {
		if (!options.isEmpty()) {
			parts.add(prefix + options.stream()
				.filter(Objects::nonNull)
				.map(Option::label)
				.collect(Collectors.joining(separator)));
		}
	}

	private static void addText(List<String> parts, String prefix, String text) {
		List<String> values = split(text);
		if (!values.isEmpty()) {
			parts.add(prefix + String.join(", ", values));
		}
	}

	private static int count(boolean... set) {
		int count = 0;
		for (boolean value : set) {
			if (value) {
				count++;
			}
		}
		return count;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static <E extends Enum<E>> Set<E> copy(Collection<E> values, Class<E> type) {
		EnumSet<E> set = EnumSet.noneOf(type);
		if (values != null) {
			values.stream().filter(Objects::nonNull).forEach(set::add);
		}
		return set;
	}

	public String getKeywords() {
		return this.keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	public String getLocation() {
		return this.location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public Integer getDistance() {
		return this.distance;
	}

	public void setDistance(Integer distance) {
		this.distance = distance;
	}

	public Set<Workplace> getWorkplace() {
		return this.workplace;
	}

	public void setWorkplace(Set<Workplace> workplace) {
		this.workplace = copy(workplace, Workplace.class);
	}

	public Set<Seniority> getSeniority() {
		return this.seniority;
	}

	public void setSeniority(Set<Seniority> seniority) {
		this.seniority = copy(seniority, Seniority.class);
	}

	public Integer getPosted() {
		return this.posted;
	}

	public void setPosted(Integer posted) {
		this.posted = posted;
	}

	public Set<EmploymentType> getType() {
		return this.type;
	}

	public void setType(Set<EmploymentType> type) {
		this.type = copy(type, EmploymentType.class);
	}

	public RoleType getRole() {
		return this.role;
	}

	public void setRole(RoleType role) {
		this.role = role;
	}

	public Integer getExperience() {
		return this.experience;
	}

	public void setExperience(Integer experience) {
		this.experience = experience;
	}

	public Integer getPay() {
		return this.pay;
	}

	public void setPay(Integer pay) {
		this.pay = pay;
	}

	public PayPeriod getPayPeriod() {
		return this.payPeriod;
	}

	public void setPayPeriod(PayPeriod payPeriod) {
		this.payPeriod = payPeriod;
	}

	public boolean isPublishedPay() {
		return this.publishedPay;
	}

	public void setPublishedPay(boolean publishedPay) {
		this.publishedPay = publishedPay;
	}

	public Set<Department> getDepartment() {
		return this.department;
	}

	public void setDepartment(Set<Department> department) {
		this.department = copy(department, Department.class);
	}

	public Set<Industry> getIndustry() {
		return this.industry;
	}

	public void setIndustry(Set<Industry> industry) {
		this.industry = copy(industry, Industry.class);
	}

	public Set<Industry> getSkipIndustry() {
		return this.skipIndustry;
	}

	public void setSkipIndustry(Set<Industry> skipIndustry) {
		this.skipIndustry = copy(skipIndustry, Industry.class);
	}

	public CompanySize getSize() {
		return this.size;
	}

	public void setSize(CompanySize size) {
		this.size = size;
	}

	public Set<OrganizationType> getOrg() {
		return this.org;
	}

	public void setOrg(Set<OrganizationType> org) {
		this.org = copy(org, OrganizationType.class);
	}

	public boolean isNoDegree() {
		return this.noDegree;
	}

	public void setNoDegree(boolean noDegree) {
		this.noDegree = noDegree;
	}

	public String getLanguages() {
		return this.languages;
	}

	public void setLanguages(String languages) {
		this.languages = languages;
	}

	public String getCountries() {
		return this.countries;
	}

	public void setCountries(String countries) {
		this.countries = countries;
	}

	public String getCompanies() {
		return this.companies;
	}

	public void setCompanies(String companies) {
		this.companies = companies;
	}

	public String getHideCompanies() {
		return this.hideCompanies;
	}

	public void setHideCompanies(String hideCompanies) {
		this.hideCompanies = hideCompanies;
	}

	public Set<Perk> getPerk() {
		return this.perk;
	}

	public void setPerk(Set<Perk> perk) {
		this.perk = copy(perk, Perk.class);
	}

	public boolean isQuickApply() {
		return this.quickApply;
	}

	public void setQuickApply(boolean quickApply) {
		this.quickApply = quickApply;
	}

	public Integer getPerCompany() {
		return this.perCompany;
	}

	public void setPerCompany(Integer perCompany) {
		this.perCompany = perCompany;
	}

}
