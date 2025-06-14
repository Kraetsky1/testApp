package com.example.testapp.service;

import com.example.testapp.domain.EventStatusRequest;
import com.example.testapp.domain.ExternalApiResponse;
import com.example.testapp.repository.EventStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Service for handling event status changes.
 */
@Service
public class EventStatusService {
    private static final Logger logger = LoggerFactory.getLogger(EventStatusService.class);
    private final EventStatusRepository repository;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${external.api.url:http://localhost:8081/mock}")
    private String externalApiUrl;

    @Value("${kafka.topic:event-status-topic}")
    private String kafkaTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventStatusService(EventStatusRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void updateEventStatus(EventStatusRequest request) {
        try {
            logger.info("Received status update: eventId={}, status={}", request.getEventId(), request.getStatus());
            repository.save(request.getEventId(), request.getStatus());
            if (request.getStatus() == EventStatusRequest.Status.LIVE) {
                startOrUpdateJob(request.getEventId());
            } else {
                cancelJob(request.getEventId());
            }
            logger.info("Event status updated: eventId={}, status={}", request.getEventId(), request.getStatus());
        } catch (Exception e) {
            logger.error("Failed to update event status for eventId={}: {}", request.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    private void startOrUpdateJob(String eventId) {
        try {
            cancelJob(eventId); // Cancel if already scheduled
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> callExternalApiAndProcess(eventId),
                0, 10, TimeUnit.SECONDS
            );
            scheduledTasks.put(eventId, future);
            logger.info("Scheduled job for event {}", eventId);
        } catch (Exception e) {
            logger.error("Failed to schedule job for event {}: {}", eventId, e.getMessage(), e);
        }
    }

    private void cancelJob(String eventId) {
        try {
            ScheduledFuture<?> future = scheduledTasks.remove(eventId);
            if (future != null) {
                future.cancel(true);
                logger.info("Cancelled job for event {}", eventId);
            }
        } catch (Exception e) {
            logger.error("Failed to cancel job for event {}: {}", eventId, e.getMessage(), e);
        }
    }

    private void callExternalApiAndProcess(String eventId) {
        try {
            logger.info("Calling external API for event {}", eventId);
            ExternalApiResponse response = restTemplate.getForObject(externalApiUrl + "/" + eventId, ExternalApiResponse.class);
            logger.info("External API response for event {}: {}", eventId, response);
            String payload = buildPayload(eventId, response);
            publishToKafkaWithRetry(eventId, payload, 3);
        } catch (Exception e) {
            logger.error("Error calling external API for event {}: {}", eventId, e.getMessage(), e);
        }
    }

    private String buildPayload(String eventId, ExternalApiResponse response) {
        try {
            String payload = String.format("{\"eventId\":\"%s\",\"currentScore\":\"%s\"}", 
                response.getEventId(), response.getCurrentScore());
            logger.debug("Built payload for event {}: {}", eventId, payload);
            return payload;
        } catch (Exception e) {
            logger.error("Failed to build payload for event {}: {}", eventId, e.getMessage(), e);
            throw e;
        }
    }

    private void publishToKafkaWithRetry(String eventId, String payload, int maxRetries) {
        int attempt = 0;
        while (attempt < maxRetries) {
            try {
                kafkaTemplate.send(kafkaTopic, eventId, payload).get();
                logger.info("Successfully published event {} to Kafka", eventId);
                return;
            } catch (Exception e) {
                attempt++;
                logger.warn("Kafka publish failed for event {} (attempt {}/{}): {}", eventId, attempt, maxRetries, e.getMessage(), e);
                try {
                    Thread.sleep(1000L * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry sleep interrupted for event {}: {}", eventId, ie.getMessage(), ie);
                    break;
                }
            }
        }
        logger.error("Failed to publish event {} to Kafka after {} attempts", eventId, maxRetries);
    }

    public Map<String, EventStatusRequest.Status> getEventStatusMap() {
        try {
            Map<String, EventStatusRequest.Status> map = repository.findAll();
            logger.debug("Fetched event status map: {}", map);
            return map;
        } catch (Exception e) {
            logger.error("Failed to fetch event status map: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        try {
            scheduler.shutdownNow();
            logger.info("Scheduler shut down successfully.");
        } catch (Exception e) {
            logger.error("Error shutting down scheduler: {}", e.getMessage(), e);
        }
    }
} 