package com.example.integration.repository;

import com.example.integration.entity.FailedJobQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FailedJobQueueRepository extends JpaRepository<FailedJobQueueItem, Long> {
    List<FailedJobQueueItem> findTop20ByActiveTrueAndNextRetryAtBeforeOrderByNextRetryAtAsc(LocalDateTime nextRetryAt);
}
