package com.example.testapp.controller;

import com.example.testapp.domain.ExternalApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for mocking external API responses.
 */
@RestController
@RequestMapping("/mock")
public class MockExternalApiController {
    @GetMapping("/{eventId}")
    public ResponseEntity<ExternalApiResponse> getMockData(@PathVariable String eventId) {
        ExternalApiResponse response = new ExternalApiResponse();
        response.setEventId(eventId);
        response.setCurrentScore("0:0");
        return ResponseEntity.ok(response);
    }
} 