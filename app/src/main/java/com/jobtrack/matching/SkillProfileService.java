package com.jobtrack.matching;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jobtrack.documents.CvService;
import com.jobtrack.documents.CvService.CvFile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's skills, read from a CV or typed in. Reading a CV replaces the skills
 * that came from a CV before and keeps the ones the user added by hand. The CV text
 * is only held in memory while it is read.
 */
@Service
@Transactional
public class SkillProfileService {

	private static final int MAX_SKILL_LENGTH = 60;

	private final UserSkillRepository skills;

	private final SkillProfileRepository profiles;

	private final CvService cvVersions;

	private final Clock clock;

	SkillProfileService(UserSkillRepository skills, SkillProfileRepository profiles, CvService cvVersions,
			Clock clock) {
		this.skills = skills;
		this.profiles = profiles;
		this.cvVersions = cvVersions;
		this.clock = clock;
	}

	/**
	 * @return the skills found, strongest first
	 */
	public List<UserSkill> readFromCv(long ownerId, long cvVersionId) {
		CvFile file = this.cvVersions.open(ownerId, cvVersionId);
		String text;
		try (InputStream content = file.content()) {
			text = CvTextExtractor.extract(content.readAllBytes(), file.version().getContentType());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The CV could not be read", ex);
		}
		Map<String, Integer> found = SkillCatalog.find(text);
		if (found.isEmpty()) {
			throw new IllegalArgumentException(
					"No known skills were found in that CV. It may be a scanned image; you can add skills by hand.");
		}
		this.skills.deleteCvSkills(ownerId);
		this.skills.flush();
		List<String> manual = list(ownerId).stream().map(UserSkill::getName).toList();
		found.forEach((name, count) -> {
			if (manual.stream().noneMatch(name::equalsIgnoreCase)) {
				this.skills.save(new UserSkill(ownerId, name, count, "CV"));
			}
		});
		profile(ownerId).readFrom(cvVersionId, file.version().getLabel(), Instant.now(this.clock));
		return list(ownerId);
	}

	/**
	 * Adds a skill typed by the user. Known skills are stored under their catalog
	 * name, so "postgres" becomes "PostgreSQL".
	 */
	public void add(long ownerId, String typed) {
		String name = SkillCatalog.canonicalName(typed).orElseGet(() -> (typed == null) ? "" : typed.strip());
		if (name.isEmpty() || name.length() > MAX_SKILL_LENGTH) {
			throw new IllegalArgumentException("Enter a skill of up to " + MAX_SKILL_LENGTH + " characters");
		}
		boolean known = list(ownerId).stream().anyMatch((skill) -> skill.getName().equalsIgnoreCase(name));
		if (!known) {
			this.skills.save(new UserSkill(ownerId, name, 1, "MANUAL"));
			profile(ownerId).touch(Instant.now(this.clock));
		}
	}

	public void remove(long ownerId, String name) {
		this.skills.deleteByName(ownerId, name);
	}

	@Transactional(readOnly = true)
	public List<UserSkill> list(long ownerId) {
		return this.skills.findByOwnerIdOrderByWeightDescNameAsc(ownerId);
	}

	@Transactional(readOnly = true)
	public Optional<SkillProfile> profileOf(long ownerId) {
		return this.profiles.findById(ownerId);
	}

	private SkillProfile profile(long ownerId) {
		return this.profiles.findById(ownerId)
			.orElseGet(() -> this.profiles.save(new SkillProfile(ownerId, Instant.now(this.clock))));
	}

}
