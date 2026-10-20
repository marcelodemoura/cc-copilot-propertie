package br.com.mv.cccopilotpropertie.copilot.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleAsyncTimeoutWithJsonContentTypeAndStatus503() {
        AsyncRequestTimeoutException ex = new AsyncRequestTimeoutException();

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAsyncTimeout(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().get("status"));
        assertEquals("Tempo limite esgotado ao processar a requisição", response.getBody().get("error"));
    }

    @Test
    void shouldHandleUnexpectedExceptionWithJsonContentTypeAndStatus500() {
        Exception ex = new RuntimeException("Falha inesperada no servidor");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().get("status"));
    }
}
