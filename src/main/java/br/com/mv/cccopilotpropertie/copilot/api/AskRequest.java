package br.com.mv.cccopilotpropertie.copilot.api;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(
        @NotBlank String tenantId,
        @NotBlank String knowledgeBase,
        @NotBlank String question,
        String sessionId
) {
}
