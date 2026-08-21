package br.com.mv.cccopilotpropertie.llm.mock;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Profile("mock")
public class MockLlmClient implements LlmClient {

    @Override
    public ToolCallResult chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        var last = messages.get(messages.size() - 1);
        String content = String.valueOf(last.getOrDefault("content", ""));
        String preview = content.substring(0, Math.min(200, content.length()));
        return new ToolCallResult(
                "[MOCK] Resposta simulada para: " + preview,
                List.of()
        );
    }
}
