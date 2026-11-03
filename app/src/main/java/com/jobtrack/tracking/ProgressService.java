package com.jobtrack.tracking;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How the job search is going, worked out from each application's history: the
 * funnel, weekly activity, replies, which applications need a follow-up, and the
 * board. Nothing here changes data.
 */
@Service
@Transactional(readOnly = true)
public class ProgressService {

	/**
	 * Days without any news after which an open application is worth chasing.
	 */
	public static final int FOLLOW_UP_DAYS = 14;

	static final int WEEKS = 8;

	static final int CLOSED_ON_BOARD = 12;

	private static final List<Stage> PATH = List.of(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER,
			Stage.ACCEPTED);

	private static final Set<Stage> WAITING = Set.of(Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER);

	private static final Set<Stage> REPLIES = Set.of(Stage.INTERVIEW, Stage.REJECTED);

	private final JobApplicationRepository applications;

	private final Clock clock;

	ProgressService(JobApplicationRepository applications, Clock clock) {
		this.applications = applications;
		this.clock = clock;
	}

	public Progress progress(long ownerId) {
		List<JobApplication> all = this.applications.findAllWithHistory(ownerId);
		LocalDate today = LocalDate.now(this.clock);

		List<FunnelStep> funnel = new ArrayList<>();
		long previous = -1;
		for (Stage stage : PATH) {
			long reached = all.stream().filter((application) -> reached(application, stage)).count();
			Integer ofPrevious = (previous < 0) ? null : percent(reached, previous);
			funnel.add(new FunnelStep(stage, reached, percent(reached, all.size()), ofPrevious));
			previous = reached;
		}

		long applied = funnel.get(1).reached();
		List<Long> replyDays = new ArrayList<>();
		for (JobApplication application : all) {
			appliedOn(application).ifPresent((sent) -> replyOn(application)
				.ifPresent((reply) -> replyDays.add(ChronoUnit.DAYS.between(sent, reply))));
		}
		Double averageDaysToReply = replyDays.isEmpty() ? null
				: replyDays.stream().mapToLong(Long::longValue).average().orElse(0);

		LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		List<WeekCount> weeks = new ArrayList<>();
		for (int i = WEEKS - 1; i >= 0; i--) {
			LocalDate start = thisWeek.minusWeeks(i);
			LocalDate end = start.plusDays(6);
			long sent = all.stream().filter((a) -> appliedOn(a).filter((d) -> within(d, start, end)).isPresent()).count();
			long heard = all.stream().filter((a) -> replyOn(a).filter((d) -> within(d, start, end)).isPresent()).count();
			weeks.add(new WeekCount(start, sent, heard));
		}
		long busiest = weeks.stream().mapToLong((week) -> Math.max(week.applied(), week.heardBack())).max().orElse(0);

		Map<Stage, Long> outcomes = new EnumMap<>(Stage.class);
		for (Stage stage : Stage.values()) {
			outcomes.put(stage, all.stream().filter((application) -> application.getStage() == stage).count());
		}
		long open = all.stream().filter((application) -> !application.getStage().isFinal()).count();

		return new Progress(all.size(), funnel, applied, replyDays.size(), percent(replyDays.size(), applied),
				averageDaysToReply, weeks, busiest, weeks.getLast().applied(), open, outcomes,
				followUps(all, today).size());
	}

	/**
	 * Open applications with no news for {@link #FOLLOW_UP_DAYS} days or more, the
	 * longest-waiting first. A note such as "Emailed the recruiter" counts as news.
	 */
	public List<FollowUp> followUps(long ownerId) {
		return followUps(this.applications.findAllWithHistory(ownerId), LocalDate.now(this.clock));
	}

	/**
	 * Applications that are not archived, by stage. Finished ones share one column with
	 * only the most recent shown.
	 */
	public Board board(long ownerId) {
		List<JobApplication> all = this.applications.findAllWithHistory(ownerId);
		LocalDate today = LocalDate.now(this.clock);
		Map<Stage, List<BoardCard>> columns = new EnumMap<>(Stage.class);
		for (Stage stage : List.of(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER)) {
			columns.put(stage, new ArrayList<>());
		}
		List<BoardCard> closed = new ArrayList<>();
		for (JobApplication application : all) {
			if (application.isArchived()) {
				continue;
			}
			long days = ChronoUnit.DAYS.between(application.getStageSince(), today);
			boolean chase = needsFollowUp(application, today);
			BoardCard card = new BoardCard(application, days, chase);
			if (application.getStage().isFinal()) {
				closed.add(card);
			}
			else {
				columns.get(application.getStage()).add(card);
			}
		}
		Comparator<BoardCard> longestFirst = Comparator.comparingLong(BoardCard::daysInStage)
			.reversed()
			.thenComparing((card) -> card.application().getId());
		columns.values().forEach((cards) -> cards.sort(longestFirst));
		closed.sort(Comparator.comparing((BoardCard card) -> card.application().getStageSince())
			.reversed()
			.thenComparing((card) -> card.application().getId()));
		return new Board(columns, closed.stream().limit(CLOSED_ON_BOARD).toList(), closed.size());
	}

	private static List<FollowUp> followUps(List<JobApplication> all, LocalDate today) {
		return all.stream()
			.filter((application) -> needsFollowUp(application, today))
			.map((application) -> new FollowUp(application, application.getLastActivityOn(),
					ChronoUnit.DAYS.between(application.getLastActivityOn(), today)))
			.sorted(Comparator.comparingLong(FollowUp::daysQuiet)
				.reversed()
				.thenComparing((followUp) -> followUp.application().getId()))
			.toList();
	}

	private static boolean needsFollowUp(JobApplication application, LocalDate today) {
		return !application.isArchived() && WAITING.contains(application.getStage())
				&& ChronoUnit.DAYS.between(application.getLastActivityOn(), today) >= FOLLOW_UP_DAYS;
	}

	private static boolean reached(JobApplication application, Stage stage) {
		return application.getStage() == stage
				|| application.getHistory().stream().anyMatch((entry) -> entry.getToStage() == stage);
	}

	private static Optional<LocalDate> appliedOn(JobApplication application) {
		return application.getHistory()
			.stream()
			.filter((entry) -> entry.getType() == EventType.STAGE_CHANGE && entry.getToStage() == Stage.APPLIED)
			.map(HistoryEntry::getOccurredOn)
			.findFirst();
	}

	/**
	 * The day the employer answered an application: an interview invitation or a
	 * rejection straight after applying.
	 */
	private static Optional<LocalDate> replyOn(JobApplication application) {
		return application.getHistory()
			.stream()
			.filter((entry) -> entry.getType() == EventType.STAGE_CHANGE && entry.getFromStage() == Stage.APPLIED
					&& REPLIES.contains(entry.getToStage()))
			.map(HistoryEntry::getOccurredOn)
			.findFirst();
	}

	private static boolean within(LocalDate day, LocalDate start, LocalDate end) {
		return !day.isBefore(start) && !day.isAfter(end);
	}

	private static Integer percent(long part, long whole) {
		return (whole == 0) ? null : (int) Math.round(100.0 * part / whole);
	}

	/**
	 * @param total every application, archived ones included
	 * @param heardBack applications that got an interview or a rejection after applying
	 * @param responsePercent heard back as a share of applied, or {@code null} before
	 * the first application
	 * @param busiestWeek the largest weekly number, to scale the chart
	 * @param outcomes applications currently in each stage
	 */
	public record Progress(long total, List<FunnelStep> funnel, long applied, long heardBack, Integer responsePercent,
			Double averageDaysToReply, List<WeekCount> weeks, long busiestWeek, long appliedThisWeek, long open,
			Map<Stage, Long> outcomes, long followUpCount) {

		public int barPercent(long value) {
			return (this.busiestWeek == 0) ? 0 : (int) Math.round(100.0 * value / this.busiestWeek);
		}

	}

	/**
	 * @param reached applications that got to this stage at some point
	 * @param percentOfTotal share of all applications
	 * @param percentOfPrevious share of the step before, or {@code null} for the first
	 */
	public record FunnelStep(Stage stage, long reached, Integer percentOfTotal, Integer percentOfPrevious) {
	}

	public record WeekCount(LocalDate weekStart, long applied, long heardBack) {
	}

	public record FollowUp(JobApplication application, LocalDate lastActivityOn, long daysQuiet) {
	}

	public record BoardCard(JobApplication application, long daysInStage, boolean needsFollowUp) {
	}

	/**
	 * @param closed the most recently finished applications
	 * @param closedTotal how many finished applications there are in all
	 */
	public record Board(Map<Stage, List<BoardCard>> columns, List<BoardCard> closed, int closedTotal) {
	}

}
