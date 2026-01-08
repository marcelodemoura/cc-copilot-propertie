package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.copilot.rag.application.RagService;
import org.springframework.stereotype.Service;
@Service
public class CopilotService {

    private final RagService rag;
    private final CopilotHistoryService history;

    public CopilotService(
            RagService rag,
            CopilotHistoryService history
    ) {
        this.rag = rag;
        this.history = history;
    }

    public CopilotAnswer ask(
            String tenantId,
            String knowledgeBase,
            String question
    ) {
        CopilotAnswer answer =
                rag.ask(tenantId, knowledgeBase, question);

        history.save(tenantId, knowledgeBase, question, answer);

        return answer;
    }
}

