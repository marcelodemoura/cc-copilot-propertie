package br.com.mv.cccopilotpropertie.copilot.history.api;

import java.time.Instant;

public record CopilotHistoryResponse(
        String tenantId,
        String knowledgeBase,
        String question,
        String answer,
        double confidence,
        Instant createdAt
) {}

