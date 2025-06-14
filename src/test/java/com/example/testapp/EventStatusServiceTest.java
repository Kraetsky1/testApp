package com.example.testapp;

import com.example.testapp.model.EventStatusRequest;
import com.example.testapp.repository.EventStatusRepository;
import com.example.testapp.service.EventStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.junit.jupiter.api.AfterEach;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EventStatusServiceTest {
    private EventStatusRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private EventStatusService service;
    private static final Logger logger = LoggerFactory.getLogger(EventStatusServiceTest.class);

    @BeforeEach
    void setUp() {
        repository = new EventStatusRepository();
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new EventStatusService(repository, kafkaTemplate);
    }

    @Test
    void testUpdateEventStatus_liveSchedulesJob() {
        EventStatusRequest req = new EventStatusRequest();
        req.setEventId("testId");
        req.setStatus(EventStatusRequest.Status.LIVE);
        service.updateEventStatus(req);
        assertEquals(EventStatusRequest.Status.LIVE, repository.findStatus("testId"));
    }

    @Test
    void testUpdateEventStatus_notLiveCancelsJob() {
        EventStatusRequest req = new EventStatusRequest();
        req.setEventId("testId");
        req.setStatus(EventStatusRequest.Status.NOT_LIVE);
        service.updateEventStatus(req);
        assertEquals(EventStatusRequest.Status.NOT_LIVE, repository.findStatus("testId"));
    }

    @Test
    void testGetEventStatusMap() {
        EventStatusRequest req = new EventStatusRequest();
        req.setEventId("testId");
        req.setStatus(EventStatusRequest.Status.LIVE);
        service.updateEventStatus(req);
        Map<String, EventStatusRequest.Status> map = service.getEventStatusMap();
        assertTrue(map.containsKey("testId"));
    }

    @Test
    void testPublishToKafkaWithRetry_successOnFirstTry() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Act
        serviceTestablePublishToKafkaWithRetry("eventX", "payload", 3);

        // Assert
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishToKafkaWithRetry_retriesOnFailure() throws Exception {
        // Arrange
        CompletableFuture<SendResult<String, String>> failedFuture = CompletableFuture.failedFuture(new RuntimeException("Kafka error"));
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture)
                .thenReturn(successFuture);

        // Act
        serviceTestablePublishToKafkaWithRetry("eventY", "payload", 3);

        // Assert
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), anyString());
    }

    // Helper to access protected method for testing
    private void serviceTestablePublishToKafkaWithRetry(String eventId, String payload, int maxRetries) {
        try {
            var method = service.getClass().getDeclaredMethod("publishToKafkaWithRetry", String.class, String.class, int.class);
            method.setAccessible(true);
            method.invoke(service, eventId, payload, maxRetries);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up if needed
    }
} 