package br.com.mv.cccopilotpropertie.project.api;

import jakarta.validation.constraints.NotBlank;

public record ProjectModifyFileRequest(
        @NotBlank String path,
        @NotBlank String targetContent,
        @NotBlank String replacementContent,
        String reason
) {
}
