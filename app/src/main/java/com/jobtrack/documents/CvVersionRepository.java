package com.jobtrack.documents;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface CvVersionRepository extends JpaRepository<CvVersion, Long> {

	List<CvVersion> findByOwnerIdOrderByUploadedAtDescIdDesc(Long ownerId);

	Optional<CvVersion> findByIdAndOwnerId(Long id, Long ownerId);

}
