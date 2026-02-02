package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.rag.application.RagService;
import org.springframework.stereotype.Service;

@Service
public class CopilotService {

    private final RagService ragService;

    public CopilotService(RagService ragService) {
        this.ragService = ragService;
    }

    public CopilotAnswer ask(String tenantId, String kb, String question) {
        return ragService.ask(tenantId, kb, question);
    }
}
