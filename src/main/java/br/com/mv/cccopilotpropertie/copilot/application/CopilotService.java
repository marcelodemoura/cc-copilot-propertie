package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.rag.application.RagService;
import org.springframework.stereotype.Service;

@Service
public class CopilotService {

    private final RagService rag;

    public CopilotService(RagService rag) {
        this.rag = rag;
    }

    public String ask(String question) {
        return rag.ask(question);
    }
}
