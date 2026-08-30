package com.example.integration.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class RuntimeConfig {

    @Bean(name = "integrationTaskExecutor")
    public Executor integrationTaskExecutor(
            @Value("${integration.executor.pool-size:64}") int poolSize,
            @Value("${integration.executor.queue-capacity:1024}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("integration-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean(name = "executionWorkerExecutor")
    public Executor executionWorkerExecutor(
            @Value("${integration.scalable.worker.max-concurrency:128}") int poolSize,
            @Value("${integration.scalable.worker.queue-capacity:2048}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("execution-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler integrationTaskScheduler(
            @Value("${integration.scheduler.pool-size:16}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("integration-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public WebClient.Builder webClientBuilder(
            @Value("${integration.http-client.max-connections:1000}") int maxConnections,
            @Value("${integration.http-client.pending-acquire-max-count:4000}") int pendingAcquireMaxCount,
            @Value("${integration.http-client.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${integration.http-client.response-timeout-seconds:90}") int responseTimeoutSeconds) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("integration-http")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMaxCount)
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(10))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .followRedirect(true)
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    public WebClient.Builder webClientBuilder() {
        return webClientBuilder(1000, 4000, 5000, 90);
    }
}
