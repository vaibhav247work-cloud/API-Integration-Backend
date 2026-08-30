package com.example.integration.repository;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.model.enums.ExecutionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, Long> {
    Optional<ExecutionJob> findByDedupeKey(String dedupeKey);

    List<ExecutionJob> findByStatusOrderByPlannedFireAtAsc(ExecutionJobStatus status);

    List<ExecutionJob> findByIntegrationIdOrderByPlannedFireAtDesc(Long integrationId);

    List<ExecutionJob> findByStatusInAndNextAttemptAtBeforeOrderByPlannedFireAtAsc(
            List<ExecutionJobStatus> statuses,
            LocalDateTime nextAttemptAt);

    List<ExecutionJob> findByStatusInAndLeaseUntilBefore(
            List<ExecutionJobStatus> statuses,
            LocalDateTime leaseUntil);

    long countByStatus(ExecutionJobStatus status);

    @Query("""
            select j
            from ExecutionJob j
            where (:integrationId is null or j.integrationId = :integrationId)
              and (:statuses is null or j.status in :statuses)
              and (:fromTime is null or j.plannedFireAt >= :fromTime)
              and (:toTime is null or j.plannedFireAt <= :toTime)
            order by j.plannedFireAt desc, j.id desc
            """)
    List<ExecutionJob> search(
            Long integrationId,
            List<ExecutionJobStatus> statuses,
            LocalDateTime fromTime,
            LocalDateTime toTime);
}
