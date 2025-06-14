package com.example.testapp.service;

import com.example.testapp.domain.EventStatusRequest;
import com.example.testapp.repository.EventStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EventStatusServiceTest {
    private EventStatusRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private EventStatusService service;

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
    void testServiceHandlesKafkaErrorsGracefully() {
        CompletableFuture<SendResult<String, String>> failedFuture = CompletableFuture.failedFuture(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        EventStatusRequest req = new EventStatusRequest();
        req.setEventId("testId");
        req.setStatus(EventStatusRequest.Status.LIVE);

        assertDoesNotThrow(() -> service.updateEventStatus(req));
    }
} 