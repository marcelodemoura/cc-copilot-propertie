package br.com.mv.cccopilotpropertie.copilot.rag.application;


import br.com.mv.cccopilotpropertie.llm.OpenAiChatService;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private final OpenAiChatService llm;

    public AnswerService(OpenAiChatService llm) {
        this.llm = llm;
    }

    public String ask(String prompt) {
        return llm.complete(prompt);
    }
}
