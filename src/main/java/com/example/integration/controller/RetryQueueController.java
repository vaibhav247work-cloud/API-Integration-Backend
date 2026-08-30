package com.example.integration.controller;

import com.example.integration.entity.FailedJobQueueItem;
import com.example.integration.service.RetryQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/failures")
@RequiredArgsConstructor
public class RetryQueueController {

    private final RetryQueueService retryQueueService;

    @GetMapping
    public List<FailedJobQueueItem> findAll() {
        return retryQueueService.findAll();
    }

    @PostMapping("/{id}/retry")
    public Map<String, Object> retryNow(@PathVariable Long id) {
        retryQueueService.triggerRetry(id);
        return Map.of("message", "Retry submitted", "queueId", id);
    }
}
