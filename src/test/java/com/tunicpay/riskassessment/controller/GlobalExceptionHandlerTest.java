package com.tunicpay.riskassessment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBadRequestReturns400WithMessage() {
        var ex = new IllegalArgumentException("Missing parameter");

        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing parameter", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleUpstreamFailureReturns503() {
        var ex = new UpstreamFailureException("API unreachable");

        ResponseEntity<Map<String, Object>> response = handler.handleUpstreamFailure(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("API unreachable", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleUnexpectedReturns500WithGenericMessage() {
        var ex = new NullPointerException("something broke");

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().get("error"));
        assertNotNull(response.getBody().get("timestamp"));
    }

    @Test
    void handleUnexpectedDoesNotLeakExceptionDetails() {
        var ex = new RuntimeException("sensitive internal detail");

        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(ex);

        assertFalse(response.getBody().get("error").toString().contains("sensitive"));
    }
}
