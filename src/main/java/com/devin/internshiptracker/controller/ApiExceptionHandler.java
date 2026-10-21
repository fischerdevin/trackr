package com.devin.internshiptracker.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", exception.getReason() == null ? "Request failed" : exception.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException exception) {
        return Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "message", exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(java.util.stream.Collectors.joining("; ")));
    }
}
