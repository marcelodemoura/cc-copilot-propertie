package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.llm.apllication.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {
    private final LlmClient llm;

    public AnswerService(LlmClient llm) {
        this.llm = llm;
    }

    public String answer(String prompt) {
        return llm.complete(prompt);
    }
}
