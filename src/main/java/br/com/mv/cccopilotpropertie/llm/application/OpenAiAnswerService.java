package br.com.mv.cccopilotpropertie.llm.application;

import br.com.mv.cccopilotpropertie.copilot.rag.application.AnswerService;
import org.springframework.stereotype.Service;

@Service
public class OpenAiAnswerService extends AnswerService {

    private final LlmClient llm;

    public OpenAiAnswerService(LlmClient llm) {
        super(llm);
        this.llm = llm;
    }

    @Override
    public String ask(String prompt) {
        return llm.complete(prompt);

    }
}

