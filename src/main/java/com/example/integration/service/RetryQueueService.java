package com.example.integration.service;

import com.example.integration.config.ScalableSchedulerProperties;
import com.example.integration.entity.FailedJobQueueItem;
import com.example.integration.model.enums.RuntimeRole;
import com.example.integration.repository.FailedJobQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RetryQueueService {

    private final FailedJobQueueRepository failedJobQueueRepository;
    private final IntegrationOrchestrator integrationOrchestrator;
    private final ScalableSchedulerProperties scalableSchedulerProperties;

    @Scheduled(fixedDelayString = "${integration.retry.poll-interval-ms:60000}")
    public void retryDueJobs() {
        if (!scalableSchedulerProperties.hasRole(RuntimeRole.WORKER)) {
            return;
        }
        failedJobQueueRepository.findTop20ByActiveTrueAndNextRetryAtBeforeOrderByNextRetryAtAsc(LocalDateTime.now())
                .forEach(queueItem -> integrationOrchestrator.retryAsync(queueItem.getId()));
    }

    public List<FailedJobQueueItem> findAll() {
        return failedJobQueueRepository.findAll(Sort.by(Sort.Direction.ASC, "nextRetryAt"));
    }

    public void triggerRetry(Long queueId) {
        integrationOrchestrator.retryAsync(queueId);
    }
}
