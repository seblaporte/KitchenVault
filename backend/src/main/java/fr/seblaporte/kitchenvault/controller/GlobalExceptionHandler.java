package fr.seblaporte.kitchenvault.controller;

import fr.seblaporte.kitchenvault.exception.InvalidWeekStartException;
import fr.seblaporte.kitchenvault.generated.model.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidWeekStartException.class)
    public ResponseEntity<ErrorDto> handleInvalidWeekStart(InvalidWeekStartException ex) {
        log.warn("Invalid weekStart: {}", ex.getMessage());
        ErrorDto error = new ErrorDto();
        error.setMessage(ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorDto> handleCookidooServiceUnavailable(RestClientException ex) {
        log.error("Cookidoo service unavailable: {}", ex.getMessage());
        ErrorDto error = new ErrorDto();
        error.setMessage("Cookidoo service is unavailable");
        error.setDetails(ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorDto error = new ErrorDto();
        error.setMessage("An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
