package br.com.mv.cccopilotpropertie.copilot.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurityException(SecurityException ex) {
        log.warn("Violação de segurança detectada: {}", ex.getMessage());
        return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        java.util.Map<String, String> errors = new java.util.LinkedHashMap<>();
        for (org.springframework.validation.FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage() != null ? error.getDefaultMessage() : "Inválido");
        }
        String firstMessage = errors.values().stream().findFirst().orElse("Requisição inválida");
        return ResponseEntity.badRequest().body(Map.of(
                "error", firstMessage,
                "details", errors
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Requisição inválida"));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, String>> handleOpenAiError(HttpClientErrorException ex) {
        log.error("Erro na chamada OpenAI: {} — {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        String detail = switch (ex.getStatusCode().value()) {
            case 401 -> "OPENAI_API_KEY inválida ou não configurada.";
            case 429 -> "Limite de requisições da OpenAI atingido. Aguarde e tente novamente.";
            case 400 -> "Requisição inválida para a OpenAI: " + ex.getResponseBodyAsString();
            default  -> "Erro na OpenAI (%d): %s".formatted(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        };
        return ResponseEntity.status(502).body(Map.of("error", detail));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", "Recurso não encontrado: " + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }
}
