package com.jobtrack.jobsource;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A job search: keywords plus optional filters. An empty set or {@code null} means
 * "any". {@code excludedCompanies} leaves out jobs from those companies; it also pages
 * through results, because HiringCafe has no page number: the next page is the same
 * search without the companies already shown.
 * <p>
 * Build one with {@link #builder(String)}, or {@link #of} for the four basic fields.
 */
public record JobSearchQuery(String keywords, String location, Integer radiusKm, Set<Workplace> workplaces,
		Set<Seniority> seniorities, Set<EmploymentType> employmentTypes, RoleType roleType, Integer postedWithinDays,
		Integer maxYearsExperience, Integer minPay, PayPeriod payPeriod, boolean publishedPayOnly,
		Set<Department> departments, Set<Industry> industries, Set<Industry> excludedIndustries,
		CompanySize companySize, Set<OrganizationType> organizationTypes, boolean noDegreeRequired,
		List<String> languages, List<String> companyCountries, List<String> onlyCompanies,
		List<String> excludedCompanies, Set<Perk> perks, boolean quickApplyOnly, Integer jobsPerCompany) {

	public JobSearchQuery {
		keywords = clean(keywords);
		location = clean(location);
		workplaces = enums(workplaces, Workplace.class);
		seniorities = enums(seniorities, Seniority.class);
		employmentTypes = enums(employmentTypes, EmploymentType.class);
		departments = enums(departments, Department.class);
		industries = enums(industries, Industry.class);
		excludedIndustries = enums(excludedIndustries, Industry.class);
		organizationTypes = enums(organizationTypes, OrganizationType.class);
		perks = enums(perks, Perk.class);
		languages = names(languages);
		companyCountries = names(companyCountries);
		onlyCompanies = names(onlyCompanies);
		excludedCompanies = names(excludedCompanies);
		check(radiusKm, 1, 500, "The distance must be between 1 and 500 km");
		check(postedWithinDays, 1, 365, "Posted within must be between 1 and 365 days");
		check(maxYearsExperience, 0, 50, "Years of experience must be between 0 and 50");
		check(minPay, 0, 10_000_000, "The minimum pay must be between 0 and 10,000,000");
		check(jobsPerCompany, 1, 5, "Jobs per company must be between 1 and 5");
		if (minPay == null) {
			payPeriod = null;
		}
		else if (payPeriod == null) {
			payPeriod = PayPeriod.YEARLY;
		}
		boolean anyFilter = location != null || !workplaces.isEmpty() || !seniorities.isEmpty()
				|| !employmentTypes.isEmpty() || roleType != null || postedWithinDays != null
				|| maxYearsExperience != null || minPay != null || publishedPayOnly || !departments.isEmpty()
				|| !industries.isEmpty() || !excludedIndustries.isEmpty() || companySize != null || !organizationTypes.isEmpty() || noDegreeRequired
				|| !languages.isEmpty() || !companyCountries.isEmpty() || !onlyCompanies.isEmpty() || !perks.isEmpty()
				|| quickApplyOnly;
		if (keywords == null && !anyFilter) {
			throw new IllegalArgumentException("Enter keywords or choose at least one filter");
		}
	}

	/**
	 * The four fields of the first search form, for callers that need only those.
	 */
	public static JobSearchQuery of(String keywords, String location, Workplace workplace, Seniority seniority) {
		return builder(keywords).location(location)
			.workplaces((workplace != null) ? Set.of(workplace) : Set.of())
			.seniorities((seniority != null) ? Set.of(seniority) : Set.of())
			.build();
	}

	public static Builder builder(String keywords) {
		return new Builder(keywords);
	}

	private static String clean(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	private static List<String> names(List<String> values) {
		return (values == null) ? List.of()
				: values.stream().map(JobSearchQuery::clean).filter(Objects::nonNull).distinct().toList();
	}

	private static <E extends Enum<E>> Set<E> enums(Collection<E> values, Class<E> type) {
		EnumSet<E> set = EnumSet.noneOf(type);
		if (values != null) {
			values.stream().filter(Objects::nonNull).forEach(set::add);
		}
		return Collections.unmodifiableSet(set);
	}

	private static void check(Integer value, int min, int max, String message) {
		if (value != null && (value < min || value > max)) {
			throw new IllegalArgumentException(message);
		}
	}

	/**
	 * A choice on the search form: the value HiringCafe expects and the name shown.
	 */
	public interface Option {

		String value();

		String label();

	}

	public enum Workplace implements Option {

		REMOTE("Remote", "Remote"), HYBRID("Hybrid", "Hybrid"), ONSITE("Onsite", "On site");

		private final String value;

		private final String label;

		Workplace(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	public enum Seniority implements Option {

		NO_EXPERIENCE("No Prior Experience Required", "No experience"), ENTRY("Entry Level", "Entry"),
		MID("Mid Level", "Mid"), SENIOR("Senior Level", "Senior");

		private final String value;

		private final String label;

		Seniority(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	public enum EmploymentType implements Option {

		FULL_TIME("Full Time", "Full time"), PART_TIME("Part Time", "Part time"), CONTRACT("Contract", "Contract"),
		INTERNSHIP("Internship", "Internship"), TEMPORARY("Temporary", "Temporary");

		private final String value;

		private final String label;

		EmploymentType(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	public enum RoleType implements Option {

		INDIVIDUAL("Individual Contributor", "Not managing people"), MANAGER("People Manager", "Managing people");

		private final String value;

		private final String label;

		RoleType(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	public enum PayPeriod implements Option {

		YEARLY("Yearly", "a year"), MONTHLY("Monthly", "a month"), HOURLY("Hourly", "an hour");

		private final String value;

		private final String label;

		PayPeriod(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	public enum Department implements Option {

		SOFTWARE("Software Development"), ENGINEERING("Engineering"), IT("Information Technology"),
		DATA("Data and Analytics"), DESIGN("Design"), CREATIVE("Creative and Art Services"),
		PRODUCT("Product Management"), PROJECT("Project and Program Management"),
		OPERATIONS("Business Operations"), LEGAL("Legal and Compliance"), FINANCE("Finance and Accounting"),
		HR("Human Resources"), ADMIN("Administrative & Clerical Support"), SALES("Sales"), MARKETING("Marketing"),
		COMMUNICATIONS("Communications and Public Affairs"), BUSINESS_DEVELOPMENT("Business Development");

		private final String value;

		Department(String value) {
			this.value = value;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.value;
		}

	}

	public enum Industry implements Option {

		AI_ML("AI/ML"), AEROSPACE("Aerospace & Defense"), AGRICULTURE("Agriculture"), AUTOMOTIVE("Automotive"),
		BANKING("Banking"), BIOTECH("Biotechnology"), BLOCKCHAIN("Blockchain"), CLIMATE("Climate Tech"),
		SOFTWARE("Computer Software"), CONSULTING("Consulting"), CYBERSECURITY("Cybersecurity"),
		ECOMMERCE("E-Commerce"), EDUCATION("Education"), ENERGY("Energy"), FINTECH("Financial Technology"),
		FOOD("Food"), GOVERNMENT("Government"), WELLNESS("Health & Wellness"), HEALTHCARE("Healthcare"),
		HOSPITALITY("Hospitality"), INSURANCE("Insurance"), LEGAL("Legal"), MANUFACTURING("Manufacturing"),
		MEDIA("Media & Entertainment"), NON_PROFIT("Non-Profit"), OIL_GAS("Oil & Gas"),
		PHARMA("Pharmaceuticals"), REAL_ESTATE("Real Estate & Construction"), RETAIL("Retail"),
		ROBOTICS("Robotics"), TELECOM("Telecommunications"), TRANSPORTATION("Transportation");

		private final String value;

		Industry(String value) {
			this.value = value;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.value;
		}

	}

	public enum OrganizationType implements Option {

		PRIVATE("private", "Private company"), PUBLIC("public", "Public company"),
		NON_PROFIT("non-profit", "Non-profit"), GOVERNMENT("government", "Government");

		private final String value;

		private final String label;

		OrganizationType(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	public enum Perk implements Option {

		VISA("visa_sponsorship", "Visa sponsorship"), RELOCATION("relocation_assistance", "Relocation help"),
		FOUR_DAY_WEEK("four_day_work_week", "Four-day week"), TIME_OFF("generous_paid_time_off", "Generous time off"),
		PARENTAL_LEAVE("generous_parental_leave", "Parental leave"),
		TUITION("tuition_reimbursement", "Tuition reimbursement"), RETIREMENT("retirement_plan", "Retirement plan");

		private final String value;

		private final String label;

		Perk(String value, String label) {
			this.value = value;
			this.label = label;
		}

		@Override
		public String value() {
			return this.value;
		}

		@Override
		public String label() {
			return this.label;
		}

	}

	/**
	 * Company headcount bands. {@code null} bounds are open.
	 */
	public enum CompanySize {

		STARTUP(null, 50, "Up to 50 people"), SMALL(51, 500, "51 to 500"), LARGE(501, 5000, "501 to 5,000"),
		ENTERPRISE(5001, null, "Over 5,000");

		private final Integer minEmployees;

		private final Integer maxEmployees;

		private final String label;

		CompanySize(Integer minEmployees, Integer maxEmployees, String label) {
			this.minEmployees = minEmployees;
			this.maxEmployees = maxEmployees;
			this.label = label;
		}

		public Integer minEmployees() {
			return this.minEmployees;
		}

		public Integer maxEmployees() {
			return this.maxEmployees;
		}

		public String label() {
			return this.label;
		}

	}

	public static final class Builder {

		private final String keywords;

		private String location;

		private Integer radiusKm;

		private Set<Workplace> workplaces = Set.of();

		private Set<Seniority> seniorities = Set.of();

		private Set<EmploymentType> employmentTypes = Set.of();

		private RoleType roleType;

		private Integer postedWithinDays;

		private Integer maxYearsExperience;

		private Integer minPay;

		private PayPeriod payPeriod;

		private boolean publishedPayOnly;

		private Set<Department> departments = Set.of();

		private Set<Industry> industries = Set.of();

		private Set<Industry> excludedIndustries = Set.of();

		private CompanySize companySize;

		private Set<OrganizationType> organizationTypes = Set.of();

		private boolean noDegreeRequired;

		private List<String> languages = List.of();

		private List<String> companyCountries = List.of();

		private List<String> onlyCompanies = List.of();

		private List<String> excludedCompanies = List.of();

		private Set<Perk> perks = Set.of();

		private boolean quickApplyOnly;

		private Integer jobsPerCompany;

		private Builder(String keywords) {
			this.keywords = keywords;
		}

		public Builder location(String location) {
			this.location = location;
			return this;
		}

		public Builder radiusKm(Integer radiusKm) {
			this.radiusKm = radiusKm;
			return this;
		}

		public Builder workplaces(Set<Workplace> workplaces) {
			this.workplaces = workplaces;
			return this;
		}

		public Builder seniorities(Set<Seniority> seniorities) {
			this.seniorities = seniorities;
			return this;
		}

		public Builder employmentTypes(Set<EmploymentType> employmentTypes) {
			this.employmentTypes = employmentTypes;
			return this;
		}

		public Builder roleType(RoleType roleType) {
			this.roleType = roleType;
			return this;
		}

		public Builder postedWithinDays(Integer postedWithinDays) {
			this.postedWithinDays = postedWithinDays;
			return this;
		}

		public Builder maxYearsExperience(Integer maxYearsExperience) {
			this.maxYearsExperience = maxYearsExperience;
			return this;
		}

		public Builder minPay(Integer minPay, PayPeriod payPeriod) {
			this.minPay = minPay;
			this.payPeriod = payPeriod;
			return this;
		}

		public Builder publishedPayOnly(boolean publishedPayOnly) {
			this.publishedPayOnly = publishedPayOnly;
			return this;
		}

		public Builder departments(Set<Department> departments) {
			this.departments = departments;
			return this;
		}

		public Builder industries(Set<Industry> industries) {
			this.industries = industries;
			return this;
		}

		public Builder excludedIndustries(Set<Industry> excludedIndustries) {
			this.excludedIndustries = excludedIndustries;
			return this;
		}

		public Builder companySize(CompanySize companySize) {
			this.companySize = companySize;
			return this;
		}

		public Builder organizationTypes(Set<OrganizationType> organizationTypes) {
			this.organizationTypes = organizationTypes;
			return this;
		}

		public Builder noDegreeRequired(boolean noDegreeRequired) {
			this.noDegreeRequired = noDegreeRequired;
			return this;
		}

		public Builder languages(List<String> languages) {
			this.languages = languages;
			return this;
		}

		public Builder companyCountries(List<String> companyCountries) {
			this.companyCountries = companyCountries;
			return this;
		}

		public Builder onlyCompanies(List<String> onlyCompanies) {
			this.onlyCompanies = onlyCompanies;
			return this;
		}

		public Builder excludedCompanies(List<String> excludedCompanies) {
			this.excludedCompanies = excludedCompanies;
			return this;
		}

		public Builder perks(Set<Perk> perks) {
			this.perks = perks;
			return this;
		}

		public Builder quickApplyOnly(boolean quickApplyOnly) {
			this.quickApplyOnly = quickApplyOnly;
			return this;
		}

		public Builder jobsPerCompany(Integer jobsPerCompany) {
			this.jobsPerCompany = jobsPerCompany;
			return this;
		}

		public JobSearchQuery build() {
			return new JobSearchQuery(this.keywords, this.location, this.radiusKm, this.workplaces, this.seniorities,
					this.employmentTypes, this.roleType, this.postedWithinDays, this.maxYearsExperience, this.minPay,
					this.payPeriod, this.publishedPayOnly, this.departments, this.industries,
					this.excludedIndustries, this.companySize, this.organizationTypes, this.noDegreeRequired,
					this.languages, this.companyCountries, this.onlyCompanies, this.excludedCompanies, this.perks,
					this.quickApplyOnly, this.jobsPerCompany);
		}

	}

}
