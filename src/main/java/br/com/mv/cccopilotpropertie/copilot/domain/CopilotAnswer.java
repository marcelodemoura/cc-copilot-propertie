package br.com.mv.cccopilotpropertie.copilot.domain;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;

import java.util.List;

public record CopilotAnswer(
        String answer,
        List<Source> sources,
        double confidence,
        Object structured,
        AlertResult alert
        ) {

    public record Source(
            String path,
            double score
    ) {}
}
