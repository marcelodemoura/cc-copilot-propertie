package br.com.mv.cccopilotpropertie.llm.ollama;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Profile("ollama")
public class OllamaChatService implements LlmClient {

    private final String baseUrl;
    private final String model;
    private final RestTemplate rest;

    public OllamaChatService(
            @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${llm.ollama.model:qwen2.5-coder}") String model
    ) {
        this(baseUrl, model, new RestTemplate());
    }

    public OllamaChatService(String baseUrl, String model, RestTemplate rest) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.rest = rest;
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

        ResponseEntity<Map> response = rest.postForEntity(
                baseUrl + "/v1/chat/completions",
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (response.getBody() == null || !response.getBody().containsKey("choices")) {
            return new ToolCallResult("", List.of());
        }

        var choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) {
            return new ToolCallResult("", List.of());
        }

        var message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return new ToolCallResult("", List.of());
        }

        var rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");

        if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
            var toolCalls = rawToolCalls.stream().map(tc -> {
                String id = (String) tc.get("id");
                if (id == null) {
                    id = "call_" + UUID.randomUUID().toString().substring(0, 8);
                }
                var fn = (Map<String, Object>) tc.get("function");
                String name = (String) fn.get("name");
                Object rawArgs = fn.get("arguments");
                String args = rawArgs instanceof String ? (String) rawArgs : String.valueOf(rawArgs);
                return new ToolCallResult.ToolCall(id, name, args);
            }).toList();
            return new ToolCallResult(null, toolCalls);
        }

        String text = (String) message.get("content");
        return new ToolCallResult(text == null ? "" : text.trim(), List.of());
    }
}
