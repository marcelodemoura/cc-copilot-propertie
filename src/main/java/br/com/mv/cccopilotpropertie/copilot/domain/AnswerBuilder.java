package br.com.mv.cccopilotpropertie.copilot.domain;

import java.util.List;

public class AnswerBuilder {

    private String message;
    private double confidence = 1.0;
    private List<CopilotAnswer.Source> sources = List.of();

    public static AnswerBuilder create() {
        return new AnswerBuilder();
    }

    public AnswerBuilder message(String message) {
        this.message = message;
        return this;
    }

    public AnswerBuilder confidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public AnswerBuilder sources(List<CopilotAnswer.Source> sources) {
        this.sources = sources;
        return this;
    }

    public CopilotAnswer build() {
        return new CopilotAnswer(
                message,
                sources,
                confidence,
                null,
                null
        );
    }
}
