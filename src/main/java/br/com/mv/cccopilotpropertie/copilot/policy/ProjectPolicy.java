package br.com.mv.cccopilotpropertie.copilot.policy;

public record ProjectPolicy(
        boolean allowContractDto,
        boolean allowInternalDtoLeak,
        boolean failOnCritical
) {
}