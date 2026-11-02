package com.jobtrack.tracking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

	@Query("""
			select a from JobApplication a join fetch a.job j
			where a.id = :id and j.ownerId = :ownerId
			""")
	Optional<JobApplication> findOwned(Long id, Long ownerId);

	Optional<JobApplication> findByJobId(Long jobId);

	@Query("""
			select a from JobApplication a join fetch a.job j
			where j.ownerId = :ownerId
			  and (:includeArchived = true or a.archived = false)
			  and (:stage is null or a.stage = :stage)
			  and (:pattern is null or lower(j.title) like :pattern or lower(j.company) like :pattern
			       or lower(j.location) like :pattern)
			order by a.updatedAt desc, a.id desc
			""")
	List<JobApplication> search(Long ownerId, Stage stage, String pattern, boolean includeArchived);

	@Query(value = """
			select a from JobApplication a join fetch a.job j
			where j.ownerId = :ownerId
			  and (:includeArchived = true or a.archived = false)
			  and (:stage is null or a.stage = :stage)
			  and (:pattern is null or lower(j.title) like :pattern or lower(j.company) like :pattern
			       or lower(j.location) like :pattern)
			order by a.updatedAt desc, a.id desc
			""", countQuery = """
			select count(a) from JobApplication a join a.job j
			where j.ownerId = :ownerId
			  and (:includeArchived = true or a.archived = false)
			  and (:stage is null or a.stage = :stage)
			  and (:pattern is null or lower(j.title) like :pattern or lower(j.company) like :pattern
			       or lower(j.location) like :pattern)
			""")
	Page<JobApplication> searchPage(Long ownerId, Stage stage, String pattern, boolean includeArchived,
			Pageable page);

	long countByJobOwnerId(Long ownerId);

	@Query("""
			select new com.jobtrack.tracking.StageCount(a.stage, count(a))
			from JobApplication a join a.job j
			where j.ownerId = :ownerId and a.archived = false
			group by a.stage
			""")
	List<StageCount> countByStage(Long ownerId);

	@Query("""
			select new com.jobtrack.tracking.TrackedExternalJob(j.externalId, a.id)
			from JobApplication a join a.job j
			where j.ownerId = :ownerId and j.sourceProvider = :provider and j.externalId in :externalIds
			""")
	List<TrackedExternalJob> findTrackedExternalJobs(Long ownerId, SourceProvider provider,
			Collection<String> externalIds);

	@Query("""
			select e from HistoryEntry e join fetch e.application a join fetch a.job j
			where j.ownerId = :ownerId
			order by e.createdAt desc, e.id desc
			""")
	List<HistoryEntry> findRecentActivity(Long ownerId, Pageable page);

}
