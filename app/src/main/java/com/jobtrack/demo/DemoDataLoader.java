package com.jobtrack.demo;

import java.time.LocalDate;

import com.jobtrack.tracking.ApplicationFilter;
import com.jobtrack.tracking.JobApplication;
import com.jobtrack.tracking.NewJob;
import com.jobtrack.tracking.Stage;
import com.jobtrack.tracking.TrackingService;
import com.jobtrack.user.AppUser;
import com.jobtrack.user.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fills the demo account with fictional applications, once. Everything goes through
 * {@link TrackingService}, so the demo data obeys the same rules as real use.
 */
@Component
@Profile("demo")
@Order(2)
class DemoDataLoader implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(DemoDataLoader.class);

	private final UserAccountService accounts;

	private final TrackingService tracking;

	DemoDataLoader(UserAccountService accounts, TrackingService tracking) {
		this.accounts = accounts;
		this.tracking = tracking;
	}

	@Override
	public void run(ApplicationArguments args) {
		AppUser demo = this.accounts.findByUsername("demo").orElse(null);
		if (demo == null || !this.tracking.list(demo.getId(), new ApplicationFilter(null, null, true)).isEmpty()) {
			return;
		}
		long owner = demo.getId();
		LocalDate today = this.tracking.today();

		JobApplication logistics = save(owner, "Junior Java Developer", "Example Logistics", "Riga",
				"https://example.com/jobs/101");
		this.tracking.changeStage(owner, logistics.getId(), Stage.APPLIED, today);
		this.tracking.changeStage(owner, logistics.getId(), Stage.INTERVIEW, today);
		this.tracking.addNote(owner, logistics.getId(),
				"First interview with the team lead. Asked about Spring transactions and SQL joins.", today);

		JobApplication health = save(owner, "Backend Intern", "Sample Health", "Remote",
				"https://example.com/jobs/202");
		this.tracking.changeStage(owner, health.getId(), Stage.APPLIED, today);

		save(owner, "Graduate Software Engineer", "Northwind Analytics", "Tallinn", "https://example.com/jobs/303");

		JobApplication retail = save(owner, "Java Support Engineer", "Contoso Retail", "Vilnius",
				"https://example.com/jobs/404");
		this.tracking.changeStage(owner, retail.getId(), Stage.APPLIED, today);
		this.tracking.changeStage(owner, retail.getId(), Stage.REJECTED, today);

		logger.info("Loaded fictional demo applications for the 'demo' account");
	}

	private JobApplication save(long owner, String title, String company, String location, String url) {
		return this.tracking.saveJob(owner, NewJob.manual(title, company, location, url));
	}

}
