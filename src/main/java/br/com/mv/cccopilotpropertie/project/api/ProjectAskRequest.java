package br.com.mv.cccopilotpropertie.project.api;

import jakarta.validation.constraints.NotBlank;

public record ProjectAskRequest(
        @NotBlank String question,
        String sessionId
) {
}
