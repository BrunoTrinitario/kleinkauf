package com.kleinkauf.users.repositories;

import com.kleinkauf.users.entities.BugReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Platform dashboard listing, newest first
public interface BugReportRepository extends JpaRepository<BugReport, Long> {
    Page<BugReport> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
    Page<BugReport> findByResolvedAtIsNullOrderByCreatedAtDescIdDesc(Pageable pageable);
    Page<BugReport> findByResolvedAtIsNotNullOrderByCreatedAtDescIdDesc(Pageable pageable);
    long countByResolvedAtIsNull();
}
