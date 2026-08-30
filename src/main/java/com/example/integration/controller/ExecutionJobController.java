package com.example.integration.controller;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.model.enums.ExecutionJobStatus;
import com.example.integration.service.execution.ExecutionJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class ExecutionJobController {

    private final ExecutionJobService executionJobService;

    @GetMapping("/{id}")
    public ExecutionJob findById(@PathVariable Long id) {
        return executionJobService.findJob(id);
    }

    @GetMapping
    public List<ExecutionJob> findAll(
            @RequestParam(required = false) Long integrationId,
            @RequestParam(required = false) List<ExecutionJobStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime) {
        return executionJobService.findJobs(integrationId, status, fromTime, toTime);
    }
}
