package com.jobtrack.events;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.jobtrack.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration V3 moves reminders out of the tracker. It is checked in a separate
 * schema: build the database as it was at V2, add reminders, then run V3.
 */
@IntegrationTest
class ReminderMigrationTest {

	private static final String SCHEMA = "migration_v3_check";

	@Autowired
	private DataSource dataSource;

	@Test
	void existingRemindersAreHandedOverThroughTheOutbox() {
		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);
		jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
		flyway("2").migrate();
		jdbc.update("insert into " + SCHEMA + ".app_user (username, password_hash) values ('migrated', 'x')");
		long user = jdbc.queryForObject("select id from " + SCHEMA + ".app_user where username = 'migrated'", Long.class);
		jdbc.update("insert into " + SCHEMA + ".tracked_job (owner_id, title, company, source_provider) values (?, 'Job', 'Company', 'MANUAL')", user);
		long job = jdbc.queryForObject("select id from " + SCHEMA + ".tracked_job where owner_id = ?", Long.class, user);
		jdbc.update("insert into " + SCHEMA + ".job_application (job_id, stage) values (?, 'SAVED')", job);
		long application = jdbc.queryForObject("select id from " + SCHEMA + ".job_application where job_id = ?", Long.class, job);
		jdbc.update("insert into " + SCHEMA + ".reminder (owner_id, application_id, due_at, note) values (?, ?, '2026-11-09T08:00:00Z', 'Open one')", user, application);
		jdbc.update("insert into " + SCHEMA + ".reminder (owner_id, application_id, due_at, note, completed_at) values (?, ?, '2026-11-05T08:00:00Z', 'Done one', '2026-11-04T10:00:00Z')", user, application);

		flyway(null).migrate();

		assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = 'reminder'", Long.class, SCHEMA)).isZero();
		List<Map<String, Object>> refs = jdbc.queryForList("select version, open from " + SCHEMA + ".reminder_ref order by version");
		assertThat(refs).extracting((row) -> row.get("version") + "/" + row.get("open")).containsExactly("1/true", "2/false");
		List<String> payloads = jdbc.queryForList("select payload from " + SCHEMA + ".outbox_event order by created_at", String.class);
		assertThat(payloads).hasSize(3);
		assertThat(String.join("\n", payloads)).contains("\"note\" : \"Open one\"", "\"note\" : \"Done one\"",
				"\"dueAt\" : \"2026-11-09T08:00:00Z\"", "REMINDER_COMPLETED");
		jdbc.execute("drop schema " + SCHEMA + " cascade");
	}

	private Flyway flyway(String target) {
		var configuration = Flyway.configure().dataSource(this.dataSource).schemas(SCHEMA).createSchemas(true);
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

}
