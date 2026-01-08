package br.com.mv.cccopilotpropertie.copilot.domain;

import java.util.List;

public record CopilotAnswer(
        String answer,
        List<Source> sources,
        double confidence
) {

    public record Source(
            String path,
            double score
    ) {
    }
}
