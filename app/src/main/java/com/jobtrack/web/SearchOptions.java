package com.jobtrack.web;

import java.util.List;

import com.jobtrack.jobsource.JobSearchQuery.CompanySize;
import com.jobtrack.jobsource.JobSearchQuery.Department;
import com.jobtrack.jobsource.JobSearchQuery.EmploymentType;
import com.jobtrack.jobsource.JobSearchQuery.Industry;
import com.jobtrack.jobsource.JobSearchQuery.OrganizationType;
import com.jobtrack.jobsource.JobSearchQuery.PayPeriod;
import com.jobtrack.jobsource.JobSearchQuery.Perk;
import com.jobtrack.jobsource.JobSearchQuery.RoleType;
import com.jobtrack.jobsource.JobSearchQuery.Seniority;
import com.jobtrack.jobsource.JobSearchQuery.Workplace;

/**
 * The choices the search form offers, in one place for the template.
 */
record SearchOptions(Workplace[] workplaces, Seniority[] seniorities, EmploymentType[] employmentTypes,
		RoleType[] roleTypes, PayPeriod[] payPeriods, Department[] departments, Industry[] industries,
		CompanySize[] companySizes, OrganizationType[] organizationTypes, Perk[] perks, List<Integer> postedDays,
		List<Integer> distancesKm) {

	static final SearchOptions INSTANCE = new SearchOptions(Workplace.values(), Seniority.values(),
			EmploymentType.values(), RoleType.values(), PayPeriod.values(), Department.values(), Industry.values(),
			CompanySize.values(), OrganizationType.values(), Perk.values(), List.of(1, 3, 7, 14, 30),
			List.of(10, 25, 50, 100, 200));

}
