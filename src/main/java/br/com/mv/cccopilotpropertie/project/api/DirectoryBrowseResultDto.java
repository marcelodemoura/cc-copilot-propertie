package br.com.mv.cccopilotpropertie.project.api;

import java.util.List;

public record DirectoryBrowseResultDto(
        String currentPath,
        String parentPath,
        String basePath,
        List<DirectoryEntryDto> directories
) {}
