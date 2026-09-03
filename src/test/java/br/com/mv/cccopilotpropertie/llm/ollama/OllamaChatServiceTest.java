package br.com.mv.cccopilotpropertie.llm.ollama;

import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaChatServiceTest {

    @Mock
    private RestTemplate rest;

    @Test
    void shouldReturnTextWhenNoToolCalls() {
        OllamaChatService service = new OllamaChatService("http://localhost:11434", "qwen2.5-coder", rest);

        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of(
                                "role", "assistant",
                                "content", "O projeto é uma aplicação Spring Boot."
                        ))
                )
        );

        when(rest.postForEntity(eq("http://localhost:11434/v1/chat/completions"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        ToolCallResult result = service.chat(
                List.of(Map.of("role", "user", "content", "Olá")),
                List.of()
        );

        assertFalse(result.hasToolCalls());
        assertEquals("O projeto é uma aplicação Spring Boot.", result.text());
    }

    @Test
    void shouldReturnToolCallsWhenPresent() {
        OllamaChatService service = new OllamaChatService("http://localhost:11434", "qwen2.5-coder", rest);

        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of(
                                "role", "assistant",
                                "tool_calls", List.of(
                                        Map.of(
                                                "id", "call_abc123",
                                                "function", Map.of(
                                                        "name", "find_dto_definition",
                                                        "arguments", "{\"className\":\"ClienteDTO\"}"
                                                )
                                        )
                                )
                        ))
                )
        );

        when(rest.postForEntity(eq("http://localhost:11434/v1/chat/completions"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        ToolCallResult result = service.chat(
                List.of(Map.of("role", "user", "content", "busca ClienteDTO")),
                List.of(Map.of("type", "function", "function", Map.of("name", "find_dto_definition")))
        );

        assertTrue(result.hasToolCalls());
        assertEquals(1, result.toolCalls().size());
        assertEquals("call_abc123", result.toolCalls().get(0).id());
        assertEquals("find_dto_definition", result.toolCalls().get(0).name());
        assertEquals("{\"className\":\"ClienteDTO\"}", result.toolCalls().get(0).argumentsJson());
    }
}
