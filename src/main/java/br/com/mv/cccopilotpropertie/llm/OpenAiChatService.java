package br.com.mv.cccopilotpropertie.llm;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Profile("openai")
public class OpenAiChatService implements LlmClient {

    private final String apiKey;
    private final String model;
    private final RestTemplate rest = new RestTemplate();

    public OpenAiChatService(
            @Value("${llm.openai.api-key}") String apiKey,
            @Value("${llm.openai.model:gpt-4.1-mini}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ToolCallResult chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.2);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> response = rest.postForEntity(
                "https://api.openai.com/v1/chat/completions",
                new HttpEntity<>(body, headers),
                Map.class
        );

        var choices = (List<Map<String, Object>>) response.getBody().get("choices");
        var message = (Map<String, Object>) choices.get(0).get("message");

        var rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");

        if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
            var toolCalls = rawToolCalls.stream().map(tc -> {
                String id = (String) tc.get("id");
                var fn = (Map<String, Object>) tc.get("function");
                String name = (String) fn.get("name");
                String args = (String) fn.get("arguments");
                return new ToolCallResult.ToolCall(id, name, args);
            }).toList();
            return new ToolCallResult(null, toolCalls);
        }

        String text = (String) message.get("content");
        return new ToolCallResult(text == null ? "" : text.trim(), List.of());
    }
}
