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

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> handleInvalidRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                    .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
                    .orElse("Requisição inválida")
                : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", ex.getClass().getSimpleName() + ": " + ex.getMessage()));
    }
}
