package br.com.mv.cccopilotpropertie.copilot.domain;

import java.util.List;

public record CopilotAnswer(
        String answer,
        List<Source> sources,
        double confidence,
        Object structured,
        Object alert,
        String suggestedPatch,
        String plan
) {

    public static CopilotAnswer simple(String message) {
        return new CopilotAnswer(
                message,
                List.of(),
                1.0,
                null,
                null,
                null,
                null
        );
    }

    public static CopilotAnswer simple(String message, double confidence) {
        return new CopilotAnswer(
                message,
                List.of(),
                confidence,
                null,
                null,
                null,
                null
        );
    }

    public record Source(String path, double score) {}
}
