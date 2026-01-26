package br.com.mv.cccopilotpropertie.copilot.domain;

import java.util.List;

public record DtoAuditResult(
        String dtoName,
        String riskLevel,
        boolean usedInOtherProjects,
        boolean hasRequiredFields,
        boolean hasExplicitValidation,
        int usageCount,
        List<String> recommendations,
        boolean isContractDto
) {}
