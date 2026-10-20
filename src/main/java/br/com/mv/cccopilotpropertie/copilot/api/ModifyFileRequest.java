package br.com.mv.cccopilotpropertie.copilot.api;

import jakarta.validation.constraints.NotBlank;

public record ModifyFileRequest(
        @NotBlank String tenantId,
        @NotBlank String knowledgeBase,
        @NotBlank String path,
        @NotBlank String targetContent,
        @NotBlank String replacementContent,
        String reason
) {
}
