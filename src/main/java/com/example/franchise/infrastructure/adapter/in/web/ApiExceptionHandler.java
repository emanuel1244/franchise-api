package com.example.franchise.infrastructure.adapter.in.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import com.example.franchise.domain.exception.NotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> domainValidation(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<Map<String, Object>> invalid(WebExchangeBindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("Solicitud invalida");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<Map<String, Object>> badInput(ServerWebInputException e) {
        return body(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud invalido");
    }

    /** Errores HTTP propios de Spring (ruta inexistente, metodo no permitido, etc.) conservan su codigo. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> springStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        return body(status, e.getReason() != null ? e.getReason() : status.getReasonPhrase());
    }

    /** Cualquier error no previsto: se registra completo y al cliente solo se le devuelve un mensaje generico. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Error no controlado", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
