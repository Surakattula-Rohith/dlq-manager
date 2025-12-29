package com.dlqmanager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Test Controller - Simple endpoints to verify the API is working
 * This helps us test that Spring Boot is responding to HTTP requests
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * Simple "Hello World" endpoint
     *
     * Access via: http://localhost:8080/api/test/hello
     *
     * @return A simple greeting message
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello from DLQ Manager! The API is working. 🚀";
    }

    /**
     * Health check endpoint - returns server status and timestamp
     *
     * Access via: http://localhost:8080/api/test/health
     *
     * @return JSON object with status information
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "DLQ Manager");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("message", "All systems operational");

        return response;
    }

    /**
     * Echo endpoint - demonstrates query parameters
     *
     * Access via: http://localhost:8080/api/test/echo?message=YourMessage
     * Example: http://localhost:8080/api/test/echo?message=Testing123
     *
     * @param message The message to echo back
     * @return JSON response with the echoed message
     */
    @GetMapping("/echo")
    public Map<String, String> echo(@RequestParam(required = false) String message) {
        Map<String, String> response = new HashMap<>();

        if (message == null || message.isEmpty()) {
            response.put("error", "No message provided");
            response.put("hint", "Try: /api/test/echo?message=YourMessage");
        } else {
            response.put("originalMessage", message);
            response.put("echoed", "You said: " + message);
            response.put("length", String.valueOf(message.length()));
        }

        return response;
    }
}
