package br.com.mv.cccopilotpropertie.llm.application;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    ToolCallResult chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    default String complete(String prompt) {
        var messages = List.of(
                Map.<String, Object>of("role", "user", "content", prompt)
        );
        return chat(messages, List.of()).text();
    }
}
