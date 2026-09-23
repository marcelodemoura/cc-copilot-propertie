package br.com.mv.cccopilotpropertie.copilot.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.warn("Timeout de requisição assíncrona: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(503, "Tempo limite esgotado ao processar a requisição", null));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(404, ex.getMessage(), null));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("Violação de segurança detectada: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(403, ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        for (org.springframework.validation.FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage() != null ? error.getDefaultMessage() : "Inválido");
        }
        String firstMessage = errors.values().stream().findFirst().orElse("Requisição inválida");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(400, firstMessage, errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(400, ex.getMessage() != null ? ex.getMessage() : "Requisição inválida", null));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, Object>> handleOpenAiError(HttpClientErrorException ex) {
        log.error("Erro na chamada OpenAI: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        String detail = switch (ex.getStatusCode().value()) {
            case 401 -> "OPENAI_API_KEY inválida ou não configurada.";
            case 429 -> "Limite de requisições da OpenAI atingido. Aguarde e tente novamente.";
            case 400 -> "Requisição inválida para a OpenAI: " + ex.getResponseBodyAsString();
            default  -> "Erro na OpenAI (%d): %s".formatted(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        };
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(502, detail, null));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(404, "Recurso não encontrado: " + ex.getResourcePath(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildErrorBody(500, ex.getClass().getSimpleName() + ": " + ex.getMessage(), null));
    }

    private Map<String, Object> buildErrorBody(int status, String error, Object details) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", status);
        body.put("error", error);
        if (details != null) {
            body.put("details", details);
        }
        body.put("timestamp", java.time.OffsetDateTime.now().toString());
        return body;
    }
}
