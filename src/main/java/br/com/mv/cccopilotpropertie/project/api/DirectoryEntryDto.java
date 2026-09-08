package br.com.mv.cccopilotpropertie.project.api;

public record DirectoryEntryDto(
        String name,
        String path,
        boolean isProject,
        String projectType
) {}
