package br.com.mv.cccopilotpropertie.llm;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAiChatService implements LlmClient {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate rest = new RestTemplate();

    @Override
    public String complete(String prompt) {

        Map<String, Object> body = Map.of(
                "model", "gpt-4.1-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "Você é um copiloto técnico do sistema MV."),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, h);

        ResponseEntity<Map> res = rest.postForEntity(
                "https://api.openai.com/v1/chat/completions",
                req,
                Map.class
        );

        var choices = (List<Map<String,Object>>) res.getBody().get("choices");
        var msg = (Map<String,Object>) choices.get(0).get("message");

        return msg.get("content").toString();
    }
}
