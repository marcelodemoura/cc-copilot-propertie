package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;

public abstract class AnswerService {

    private final LlmClient llm;

    protected AnswerService(LlmClient llm) {
        this.llm = llm;
    }

    public String ask(String prompt) {
        return llm.complete(prompt);
    }
}
