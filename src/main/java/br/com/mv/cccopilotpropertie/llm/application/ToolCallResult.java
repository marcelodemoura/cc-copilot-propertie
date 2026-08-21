package br.com.mv.cccopilotpropertie.llm.application;

import java.util.List;

public record ToolCallResult(
        String text,
        List<ToolCall> toolCalls
) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public record ToolCall(String id, String name, String argumentsJson) {}
}
