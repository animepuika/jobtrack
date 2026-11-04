package com.jobtrack.matching;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skills JobTrack can recognise in CVs and job descriptions, each with the ways it is
 * usually written. Patterns are careful about look-alikes: "Java" must not match
 * "JavaScript", and words like "rest" or "spring" only count in a technical phrase.
 * No AI is involved: this is plain text matching, so every result can be explained.
 */
public final class SkillCatalog {

	private static final Map<String, Pattern> SKILLS = new LinkedHashMap<>();

	static {
		// Languages
		add("Java", "\\bjava\\b(?!\\s*script)");
		add("Kotlin", "\\bkotlin\\b");
		add("Scala", "\\bscala\\b");
		add("Python", "\\bpython\\b");
		add("JavaScript", "\\bjavascript\\b|\\bjs\\b(?![./])|\\becmascript\\b");
		add("TypeScript", "\\btypescript\\b");
		add("C#", "(?<![\\w#])c#|\\bc\\s?sharp\\b");
		add("C++", "(?<![\\w+])c\\+\\+");
		add("Go", "\\bgolang\\b|\\bgo\\s+(?:developer|engineer|language|programming)\\b");
		add("Rust", "\\brust\\b(?!\\s*belt)");
		add("PHP", "\\bphp\\b");
		add("Ruby", "\\bruby\\b");
		add("Swift", "\\bswift\\b(?!\\s*code)");
		add("SQL", "\\bsql\\b");
		add("Bash", "\\bbash\\b|\\bshell\\s+script(?:ing|s)?\\b");
		add("HTML", "\\bhtml5?\\b");
		add("CSS", "\\bcss3?\\b|\\bsass\\b|\\bscss\\b");
		// Java and web frameworks
		add("Spring Boot", "\\bspring\\s*boot\\b");
		add("Spring", "\\bspring\\s+(?:framework|mvc|security|data|cloud|batch|webflux)\\b");
		add("Hibernate", "\\bhibernate\\b");
		add("JPA", "\\bjpa\\b|jakarta persistence|java persistence");
		add("Jakarta EE", "\\bjakarta\\s*ee\\b|\\bjava\\s*ee\\b|\\bj2ee\\b");
		add("Quarkus", "\\bquarkus\\b");
		add("Micronaut", "\\bmicronaut\\b");
		add("Thymeleaf", "\\bthymeleaf\\b");
		add("React", "\\breact(?:\\.js|js)?\\b(?!\\s*native)");
		add("Angular", "\\bangular(?:js)?\\b");
		add("Vue", "\\bvue(?:\\.js|js)?\\b");
		add("Node.js", "\\bnode\\.?js\\b");
		add(".NET", "(?<![\\w.])\\.net\\b|\\bdotnet\\b|\\basp\\.net\\b");
		add("Django", "\\bdjango\\b");
		add("Flask", "\\bflask\\b");
		// Data and messaging
		add("PostgreSQL", "\\bpostgres(?:ql)?\\b");
		add("MySQL", "\\bmysql\\b|\\bmariadb\\b");
		add("Oracle Database", "\\boracle\\s+(?:db|database|sql)\\b|\\bpl/sql\\b");
		add("SQL Server", "\\bsql\\s+server\\b|\\bmssql\\b|\\bt-sql\\b");
		add("MongoDB", "\\bmongo(?:db)?\\b");
		add("Redis", "\\bredis\\b");
		add("Elasticsearch", "\\belastic\\s*search\\b|\\bopensearch\\b");
		add("Kafka", "\\bkafka\\b");
		add("RabbitMQ", "\\brabbit\\s*mq\\b");
		add("Flyway", "\\bflyway\\b");
		add("Liquibase", "\\bliquibase\\b");
		// Cloud, delivery and tools
		add("Docker", "\\bdocker\\b|\\bcontaineri[sz](?:ed|ation)\\b");
		add("Kubernetes", "\\bkubernetes\\b|\\bk8s\\b|\\baks\\b|\\beks\\b|\\bgke\\b|\\bopenshift\\b");
		add("Azure", "\\bazure\\b");
		add("AWS", "\\baws\\b|amazon web services");
		add("Google Cloud", "\\bgcp\\b|google cloud");
		add("Terraform", "\\bterraform\\b");
		add("Bicep", "\\bbicep\\b");
		add("CI/CD", "\\bci\\s*/\\s*cd\\b|continuous (?:integration|delivery|deployment)");
		add("GitHub Actions", "\\bgithub\\s+actions\\b");
		add("Jenkins", "\\bjenkins\\b");
		add("Git", "\\bgit\\b(?!hub|lab)");
		add("Linux", "\\blinux\\b|\\bunix\\b");
		add("Maven", "\\bmaven\\b");
		add("Gradle", "\\bgradle\\b");
		// Architecture and practices
		add("REST APIs", "\\brestful\\b|\\brest\\s*(?:api|apis|services|endpoints|web\\s*services)\\b");
		add("GraphQL", "\\bgraphql\\b");
		add("Microservices", "\\bmicro-?services?\\b");
		add("JUnit", "\\bjunit\\b");
		add("Mockito", "\\bmockito\\b");
		add("Testcontainers", "\\btestcontainers\\b");
		add("Test automation", "\\btest\\s+automation\\b|\\bautomated\\s+test(?:s|ing)\\b|\\bselenium\\b|\\bplaywright\\b|\\bcypress\\b");
		add("TDD", "\\btdd\\b|test[- ]driven");
		add("Agile", "\\bagile\\b|\\bscrum\\b|\\bkanban\\b");
		add("OAuth", "\\boauth\\s*2?(?:\\.0)?\\b|\\bopenid\\s+connect\\b|\\boidc\\b");
		add("Domain-driven design", "domain[- ]driven design|\\bddd\\b");
	}

	private static final Set<String> SEARCH_WORTHY = Set.of("Java", "Kotlin", "Scala", "Python", "JavaScript",
			"TypeScript", "C#", "C++", "Go", "Rust", "PHP", "Ruby", "Swift", "Spring Boot", "Spring", "Jakarta EE",
			"Quarkus", "Micronaut", "React", "Angular", "Vue", "Node.js", ".NET", "Django", "Flask");

	private SkillCatalog() {
	}

	private static void add(String name, String regex) {
		SKILLS.put(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
	}

	/**
	 * Every known skill found in the text, with how many times it appears, in catalog
	 * order.
	 */
	public static Map<String, Integer> find(String text) {
		Map<String, Integer> found = new LinkedHashMap<>();
		if (text == null || text.isBlank()) {
			return found;
		}
		SKILLS.forEach((name, pattern) -> {
			Matcher matcher = pattern.matcher(text);
			int count = 0;
			while (matcher.find()) {
				count++;
			}
			if (count > 0) {
				found.put(name, count);
			}
		});
		return found;
	}

	/**
	 * The catalog's name for a skill typed by the user, so "postgres" and "PostgreSQL"
	 * are the same skill. Empty if the catalog does not know it.
	 */
	public static Optional<String> canonicalName(String typed) {
		if (typed == null || typed.isBlank()) {
			return Optional.empty();
		}
		String value = typed.strip();
		for (Map.Entry<String, Pattern> skill : SKILLS.entrySet()) {
			if (skill.getKey().equalsIgnoreCase(value) || fullMatch(skill.getValue(), value)) {
				return Optional.of(skill.getKey());
			}
		}
		return Optional.empty();
	}

	/**
	 * Whether a skill appears in the text: by its catalog pattern if it has one,
	 * otherwise as the literal words the user typed.
	 */
	public static boolean mentions(String text, String skill) {
		if (text == null || text.isBlank()) {
			return false;
		}
		Pattern pattern = SKILLS.get(skill);
		if (pattern == null) {
			pattern = Pattern.compile("(?<![\\w])" + Pattern.quote(skill.toLowerCase(Locale.ROOT)) + "(?![\\w])",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		}
		return pattern.matcher(text).find();
	}

	/**
	 * The user's skills that make the best search words: programming languages and
	 * frameworks, most mentioned first, then in catalog order. Tools such as Git appear
	 * in almost every posting, so they are used only when nothing better is there.
	 */
	public static List<String> searchTerms(List<UserSkill> skills, int max) {
		List<String> catalogOrder = List.copyOf(SKILLS.keySet());
		Comparator<UserSkill> strongestFirst = Comparator.comparingInt(UserSkill::getWeight)
			.reversed()
			.thenComparingInt((skill) -> {
				int index = catalogOrder.indexOf(skill.getName());
				return (index < 0) ? Integer.MAX_VALUE : index;
			});
		List<String> worthy = skills.stream()
			.filter((skill) -> SEARCH_WORTHY.contains(skill.getName()))
			.sorted(strongestFirst)
			.map(UserSkill::getName)
			.filter((name) -> !name.equals("Spring") || skills.stream().noneMatch((s) -> s.getName().equals("Spring Boot")))
			.limit(max)
			.toList();
		if (!worthy.isEmpty()) {
			return worthy;
		}
		return skills.stream().sorted(strongestFirst).map(UserSkill::getName).limit(max).toList();
	}

	private static boolean fullMatch(Pattern pattern, String value) {
		Matcher matcher = pattern.matcher(value);
		return matcher.find() && matcher.start() == 0 && matcher.end() == value.length();
	}

}
