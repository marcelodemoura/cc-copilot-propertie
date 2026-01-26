package br.com.mv.cccopilotpropertie.copilot.audit;

public record AuditSummary(
        String key,
        long total,
        long critical,
        long highRisk
) {}

