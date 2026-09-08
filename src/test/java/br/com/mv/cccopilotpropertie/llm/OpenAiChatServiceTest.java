package br.com.mv.cccopilotpropertie.llm;

import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiChatServiceTest {

    @Mock
    private RestTemplate rest;

    @Test
    void shouldOmitTemperatureWhenNotSpecified() {
        OpenAiChatService service = new OpenAiChatService("test-key", "gpt-5-mini", null, rest);

        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of(
                                "role", "assistant",
                                "content", "Olá mundo!"
                        ))
                )
        );

        when(rest.postForEntity(eq("https://api.openai.com/v1/chat/completions"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        ToolCallResult result = service.chat(
                List.of(Map.of("role", "user", "content", "Oi")),
                List.of()
        );

        assertFalse(result.hasToolCalls());
        assertEquals("Olá mundo!", result.text());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).postForEntity(eq("https://api.openai.com/v1/chat/completions"), captor.capture(), eq(Map.class));

        Map<String, Object> sentBody = captor.getValue().getBody();
        assertNotNull(sentBody);
        assertFalse(sentBody.containsKey("temperature"), "Temperatura não deve ser enviada quando for nula");
        assertEquals("gpt-5-mini", sentBody.get("model"));
    }

    @Test
    void shouldIncludeTemperatureWhenSpecified() {
        OpenAiChatService service = new OpenAiChatService("test-key", "gpt-4o", 0.7, rest);

        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of(
                                "role", "assistant",
                                "content", "Resposta criativa"
                        ))
                )
        );

        when(rest.postForEntity(eq("https://api.openai.com/v1/chat/completions"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        ToolCallResult result = service.chat(
                List.of(Map.of("role", "user", "content", "Crie algo")),
                List.of()
        );

        assertFalse(result.hasToolCalls());
        assertEquals("Resposta criativa", result.text());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).postForEntity(eq("https://api.openai.com/v1/chat/completions"), captor.capture(), eq(Map.class));

        Map<String, Object> sentBody = captor.getValue().getBody();
        assertNotNull(sentBody);
        assertEquals(0.7, sentBody.get("temperature"));
    }

    @Test
    void shouldParseToolCallsCorrectly() {
        OpenAiChatService service = new OpenAiChatService("test-key", "gpt-5-mini", null, rest);

        Map<String, Object> responseBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of(
                                "role", "assistant",
                                "tool_calls", List.of(
                                        Map.of(
                                                "id", "call-123",
                                                "function", Map.of(
                                                        "name", "read_file",
                                                        "arguments", "{\"path\":\"README.md\"}"
                                                )
                                        )
                                )
                        ))
                )
        );

        when(rest.postForEntity(eq("https://api.openai.com/v1/chat/completions"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        ToolCallResult result = service.chat(
                List.of(Map.of("role", "user", "content", "Leia o readme")),
                List.of(Map.of("type", "function", "function", Map.of("name", "read_file")))
        );

        assertTrue(result.hasToolCalls());
        assertEquals(1, result.toolCalls().size());
        assertEquals("call-123", result.toolCalls().get(0).id());
        assertEquals("read_file", result.toolCalls().get(0).name());
        assertEquals("{\"path\":\"README.md\"}", result.toolCalls().get(0).argumentsJson());
    }
}
