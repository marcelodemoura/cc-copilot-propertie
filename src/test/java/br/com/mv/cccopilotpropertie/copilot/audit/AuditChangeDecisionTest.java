package br.com.mv.cccopilotpropertie.copilot.audit;

import br.com.mv.cccopilotpropertie.copilot.agent.AgentToolExecutor;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingChangeAnalyzer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditChangeDecisionTest {

    @Test
    void shouldRecordChangeDecisionDuringAnalyzeBreaking() {
        SearchRepository repository = mock(SearchRepository.class);
        AuditRepository auditRepository = mock(AuditRepository.class);
        AuditService auditService = new AuditService(auditRepository);
        SearchService searchService = mock(SearchService.class);
        BreakingChangeAnalyzer analyzer = new BreakingChangeAnalyzer();
        AlertService alertService = new AlertService();

        when(repository.findEndpointsUsingDto("tenant-1", "kb-1", "ClientDto"))
                .thenReturn(List.of(new SearchResult("ClientController.java", "@RestController", 1.0)));
        when(repository.findUsagesInOtherKnowledgeBases("tenant-1", "kb-1", "ClientDto"))
                .thenReturn(List.of());
        when(repository.findUsagesByClassName("tenant-1", "kb-1", "docNumber"))
                .thenReturn(List.of());

        AgentToolExecutor executor = new AgentToolExecutor(
                searchService, repository, analyzer, alertService, auditService
        );

        String result = executor.execute(
                "analyze_breaking_change",
                "{\"fieldName\": \"docNumber\", \"dtoName\": \"ClientDto\", \"changeType\": \"REMOVE\"}",
                "tenant-1",
                "kb-1"
        );

        assertTrue(result.contains("BREAKING"));

        // Verifica se persistiu a decisão de mudança na auditoria com tipo CHANGE_DECISION
        ArgumentCaptor<AuditExecution> captor = ArgumentCaptor.forClass(AuditExecution.class);
        verify(auditRepository).save(captor.capture());

        AuditExecution saved = captor.getValue();
        assertEquals("tenant-1", saved.getTenantId());
        assertEquals("kb-1", saved.getKnowledgeBase());
        assertEquals("ClientDto", saved.getDtoName());
        assertEquals("CHANGE_DECISION", saved.getAuditType());
        assertTrue(saved.isContractDto());
        assertEquals("BREAKING", saved.getRiskLevel());
    }
}
