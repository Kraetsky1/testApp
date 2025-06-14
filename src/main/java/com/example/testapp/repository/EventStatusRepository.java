package com.example.testapp.repository;

import com.example.testapp.domain.EventStatusRequest;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class EventStatusRepository {
    private final Map<String, EventStatusRequest.Status> eventStatusMap = new ConcurrentHashMap<>();

    public void save(String eventId, EventStatusRequest.Status status) {
        eventStatusMap.put(eventId, status);
    }

    public EventStatusRequest.Status findStatus(String eventId) {
        return eventStatusMap.get(eventId);
    }

    public Map<String, EventStatusRequest.Status> findAll() {
        return eventStatusMap;
    }
} 