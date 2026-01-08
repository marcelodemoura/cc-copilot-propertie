package br.com.mv.cccopilotpropertie.copilot.api;

public record AskRequest(
        String tenantId,
        String knowledgeBase,
        String question
) {
}
