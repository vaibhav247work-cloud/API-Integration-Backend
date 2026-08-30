package com.example.integration.controller;

import com.example.integration.entity.IntegrationRun;
import com.example.integration.repository.IntegrationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IntegrationRunController {

    private final IntegrationRunRepository integrationRunRepository;

    @GetMapping("/runs")
    public List<IntegrationRun> findAllRuns() {
        return integrationRunRepository.findAll(Sort.by(Sort.Direction.DESC, "startedAt"));
    }

    @GetMapping("/runs/{id}")
    public IntegrationRun findRunById(@PathVariable Long id) {
        return integrationRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + id));
    }

    @GetMapping("/integrations/{id}/runs")
    public List<IntegrationRun> findRunsByIntegration(@PathVariable Long id) {
        return integrationRunRepository.findByIntegrationIdOrderByStartedAtDesc(id);
    }
}
