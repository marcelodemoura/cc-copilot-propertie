package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.agent.AgentLoop;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopilotServiceTest {

    @Mock
    private AgentLoop agentLoop;

    @Mock
    private CopilotHistoryService historyService;

    @InjectMocks
    private CopilotService copilotService;

    @Test
    void should_answer_and_save_history() {
        String tenantId = "tenant-1";
        String kb = "kb-1";
        String question = "O que é RAG?";

        CopilotAnswer expected = new CopilotAnswer("resposta", List.of(), 0.9, null, null, null, null);
        when(agentLoop.run(tenantId, kb, question, null)).thenReturn(expected);

        CopilotAnswer result = copilotService.ask(tenantId, kb, question);

        assertEquals(expected, result);
        verify(agentLoop).run(tenantId, kb, question, null);
        verify(historyService).save(tenantId, kb, null, question, expected);
    }
}
