package br.com.mv.cccopilotpropertie.llm;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Double temperature;
    private final RestTemplate rest;

    @Autowired
    public OpenAiChatService(
            @Value("${llm.openai.api-key}") String apiKey,
            @Value("${llm.openai.model:gpt-4o-mini}") String model,
            @Value("${llm.openai.temperature:}") String temperature
    ) {
        this(apiKey, model, parseTemperature(temperature), createDefaultRestTemplate());
    }

    public OpenAiChatService(String apiKey, String model, Double temperature, RestTemplate rest) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.rest = rest;
    }

    private static RestTemplate createDefaultRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(15));
        factory.setReadTimeout(java.time.Duration.ofSeconds(90));
        return new RestTemplate(factory);
    }

    private static Double parseTemperature(String tempStr) {
        if (tempStr == null || tempStr.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(tempStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public ToolCallResult chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        int maxRetries = 3;
        long waitMs = 500;
        ResponseEntity<Map> response = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                response = rest.postForEntity(
                        "https://api.openai.com/v1/chat/completions",
                        new HttpEntity<>(body, headers),
                        Map.class
                );
                break;
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                boolean isRetryable = (status == 429 || status >= 500);
                if (attempt < maxRetries && isRetryable) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    waitMs *= 2;
                    continue;
                }
                throw e;
            } catch (org.springframework.web.client.ResourceAccessException e) {
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    waitMs *= 2;
                    continue;
                }
                throw e;
            }
        }

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
