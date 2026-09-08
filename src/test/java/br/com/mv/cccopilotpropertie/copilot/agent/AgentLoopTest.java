package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.infra.CopilotInteractionRepository;
import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLoopTest {

    @Mock
    private LlmClient llm;

    @Mock
    private AgentTools tools;

    @Mock
    private AgentToolExecutor executor;

    @Mock
    private CopilotInteractionRepository historyRepo;

    @Mock
    private ProjectRepository projectRepo;

    @Test
    void shouldReturnAnswerDirectlyWhenNoToolCalls() {
        when(tools.all()).thenReturn(List.of());
        when(llm.chat(any(), any())).thenReturn(new ToolCallResult("O projeto é uma API REST.", List.of()));

        AgentLoop loop = new AgentLoop(llm, tools, executor, historyRepo, projectRepo);
        CopilotAnswer answer = loop.run("tenant-1", "kb-1", "O que esse projeto faz?", "sess-1");

        assertNotNull(answer);
        assertEquals("O projeto é uma API REST.", answer.answer());
        assertEquals(1.0, answer.confidence());
    }

    @Test
    void shouldSynthesizeAnswerWhenMaxIterationsReached() {
        when(tools.all()).thenReturn(List.of(Map.of("type", "function")));

        when(llm.chat(any(), any())).thenReturn(
                new ToolCallResult(null, List.of(new ToolCallResult.ToolCall("c1", "list_files", "{\"path\":\"\"}")))
        );
        when(llm.chat(any(), eq(List.of()))).thenReturn(
                new ToolCallResult("Resposta final sintetizada após análises.", List.of())
        );
        when(executor.execute(eq("list_files"), any(), any(), any())).thenReturn("DIRETÓRIO: /app\n[DIR] src");

        AgentLoop loop = new AgentLoop(llm, tools, executor, historyRepo, projectRepo);
        CopilotAnswer answer = loop.run("tenant-1", "kb-1", "Explique a arquitetura", "sess-1");

        assertNotNull(answer);
        assertEquals("Resposta final sintetizada após análises.", answer.answer());
        assertEquals(0.85, answer.confidence());
    }
}
