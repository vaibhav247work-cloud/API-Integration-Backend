package com.example.integration.controller;

import com.example.integration.entity.ExecutionJob;
import com.example.integration.entity.IntegrationDefinition;
import com.example.integration.entity.IntegrationRun;
import com.example.integration.model.api.CustomRunRequest;
import com.example.integration.model.enums.ScheduleType;
import com.example.integration.repository.IntegrationRunRepository;
import com.example.integration.service.IntegrationDefinitionService;
import com.example.integration.service.IntegrationOrchestrator;
import com.example.integration.service.execution.ExecutionJobService;
import com.example.integration.service.schedule.IntegrationSchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationDefinitionController {

    private final IntegrationDefinitionService integrationDefinitionService;
    private final IntegrationSchedulingService integrationSchedulingService;
    private final IntegrationOrchestrator integrationOrchestrator;
    private final IntegrationRunRepository integrationRunRepository;
    private final ExecutionJobService executionJobService;

    @GetMapping
    public List<IntegrationDefinition> findAll() {
        return integrationDefinitionService.findAll();
    }

    @GetMapping("/{id}")
    public IntegrationDefinition findById(@PathVariable Long id) {
        return integrationDefinitionService.findById(id);
    }

    @PostMapping
    public ResponseEntity<IntegrationDefinition> create(@Valid @RequestBody IntegrationDefinition definition) {
        return ResponseEntity.status(HttpStatus.CREATED).body(integrationDefinitionService.save(definition));
    }

    @PutMapping("/{id}")
    public IntegrationDefinition update(@PathVariable Long id, @Valid @RequestBody IntegrationDefinition definition) {
        definition.setId(id);
        return integrationDefinitionService.save(definition);
    }

    @PatchMapping("/{id}/enabled")
    public IntegrationDefinition setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        return integrationDefinitionService.setEnabled(id, value);
    }

    @PostMapping("/{id}/run")
    public IntegrationRun runNow(@PathVariable Long id) {
        Long runId = integrationOrchestrator.runNow(id);
        return integrationRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found after execution: " + runId));
    }

    @PostMapping("/{id}/run/custom")
    public IntegrationRun runCustom(
            @PathVariable Long id,
            @RequestBody CustomRunRequest request) {
        Long runId = integrationOrchestrator.runNow(id, request.toScheduleWindow(LocalDateTime.now()));
        return integrationRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found after execution: " + runId));
    }

    @PostMapping("/{id}/enqueue")
    public ResponseEntity<ExecutionJob> enqueue(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(executionJobService.enqueueRun(id));
    }

    @PostMapping("/{id}/enqueue/custom")
    public ResponseEntity<ExecutionJob> enqueueCustom(
            @PathVariable Long id,
            @RequestBody CustomRunRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(executionJobService.enqueue(id, request.toScheduleWindow(now), now));
    }

    @PostMapping("/{id}/run/schedule")
    public IntegrationRun runSchedule(
            @PathVariable Long id,
            @RequestParam ScheduleType scheduleType) {
        Long runId = integrationSchedulingService.runScheduleNow(id, scheduleType);
        return integrationRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found after execution: " + runId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        integrationDefinitionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
