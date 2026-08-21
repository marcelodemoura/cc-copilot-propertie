package br.com.mv.cccopilotpropertie.project.api;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank String tenantId,
        @NotBlank String name,
        @NotBlank String rootPath
) {
}
