package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.agent.AgentLoop;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import org.springframework.stereotype.Service;

@Service
public class CopilotService {

    private final AgentLoop agentLoop;
    private final CopilotHistoryService historyService;

    public CopilotService(AgentLoop agentLoop, CopilotHistoryService historyService) {
        this.agentLoop = agentLoop;
        this.historyService = historyService;
    }

    public CopilotAnswer ask(String tenantId, String kb, String question, String sessionId) {
        CopilotAnswer answer = agentLoop.run(tenantId, kb, question, sessionId);
        historyService.save(tenantId, kb, sessionId, question, answer);
        return answer;
    }

    public CopilotAnswer ask(String tenantId, String kb, String question) {
        return ask(tenantId, kb, question, null);
    }
}
