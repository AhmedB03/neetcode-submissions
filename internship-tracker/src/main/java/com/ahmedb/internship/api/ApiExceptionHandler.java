package com.ahmedb.internship.api;

import com.ahmedb.internship.service.ApplicationService;
import com.ahmedb.internship.service.ReviewQueueService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns domain and validation failures into predictable JSON rather than stack traces. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** @param details field-level messages, present only for validation failures */
    public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> details) {

        static ApiError of(HttpStatus status, String message) {
            return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, null);
        }
    }

    @ExceptionHandler({
        ApplicationService.ApplicationNotFoundException.class,
        ApplicationService.CompanyNotFoundException.class,
        ReviewQueueService.UnmatchedEmailNotFoundException.class
    })
    public ResponseEntity<ApiError> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(ReviewQueueService.AlreadyResolvedException.class)
    public ResponseEntity<ApiError> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, e.getMessage()));
    }

    /**
     * Covers the domain's own guards, most importantly the refusal to store a derived status: asking
     * the override endpoint for GHOSTED is a client mistake, not a server fault.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException e) {
        Map<String, String> details = new java.util.LinkedHashMap<>();
        for (var error : e.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        List<String> globals =
                e.getBindingResult().getGlobalErrors().stream()
                        .map(error -> error.getDefaultMessage())
                        .toList();
        globals.forEach(message -> details.putIfAbsent("_", message));

        return ResponseEntity.badRequest()
                .body(
                        new ApiError(
                                Instant.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "Request validation failed",
                                details));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> dataIntegrity(
            org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Rejected a write that violates a constraint", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        ApiError.of(
                                HttpStatus.CONFLICT,
                                "That record already exists, or would violate a database constraint"));
    }
}
