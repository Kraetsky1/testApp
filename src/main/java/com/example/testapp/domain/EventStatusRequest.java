package com.example.testapp.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventStatusRequest {
    @NotBlank
    private String eventId;

    @NotNull
    private Status status;

    public enum Status {
        LIVE("live"), 
        NOT_LIVE("not_live");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
} 