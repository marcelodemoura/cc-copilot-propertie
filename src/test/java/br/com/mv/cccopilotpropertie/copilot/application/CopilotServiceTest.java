package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.copilot.rag.application.RagService;
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
    private RagService ragService;

    @Mock
    private CopilotHistoryService historyService;

    @InjectMocks
    private CopilotService copilotService;

    @Test
    void should_answer_and_save_history() {
        // given
        String tenantId = "tenant-1";
        String knowledgeBase = "kb-1";
        String question = "O que é RAG?";

        CopilotAnswer expectedAnswer = new CopilotAnswer(
                "Resposta gerada",
                List.of(),
                0.85
        );

        when(ragService.ask(tenantId, knowledgeBase, question))
                .thenReturn(expectedAnswer);

        // when
        CopilotAnswer result =
                copilotService.ask(tenantId, knowledgeBase, question);

        // then
        assertEquals(expectedAnswer, result);

        verify(ragService)
                .ask(tenantId, knowledgeBase, question);

        verify(historyService)
                .save(tenantId, knowledgeBase, question, expectedAnswer);
    }
}