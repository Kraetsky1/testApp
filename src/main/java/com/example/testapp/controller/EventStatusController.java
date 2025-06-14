package com.example.testapp.controller;

import com.example.testapp.service.EventStatusService;
import com.example.testapp.domain.EventStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling event status changes.
 */
@RestController
@RequestMapping("/events")
public class EventStatusController {
    private final EventStatusService eventStatusService;

    public EventStatusController(EventStatusService eventStatusService) {
        this.eventStatusService = eventStatusService;
    }

    @PostMapping("/status")
    public ResponseEntity<?> updateEventStatus(@Valid @RequestBody EventStatusRequest request) {
        eventStatusService.updateEventStatus(request);
        return ResponseEntity.ok().build();
    }
} 