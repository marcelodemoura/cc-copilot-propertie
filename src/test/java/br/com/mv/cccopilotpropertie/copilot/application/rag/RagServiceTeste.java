package br.com.mv.cccopilotpropertie.copilot.application.rag;

import br.com.mv.cccopilotpropertie.copilot.agent.AgentToolExecutor;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingChangeAnalyzer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RagServiceTest {

    @Test
    void deveGerarRiscoAltoParaDtoDeContratoSemValidacao() {
        SearchRepository repository = mock(SearchRepository.class);
        when(repository.findUsagesByClassName("default", "cliente-ws", "EmpresaDTO"))
                .thenReturn(List.of(new SearchResult("EmpresaDTO.java", "public class EmpresaDTO {}", 1.0)));
        when(repository.findEndpointsUsingDto("default", "cliente-ws", "EmpresaDTO"))
                .thenReturn(List.of(new SearchResult("EmpresaController.java", "@RestController", 1.0)));
        when(repository.findUsagesInOtherKnowledgeBases("default", "cliente-ws", "EmpresaDTO"))
                .thenReturn(List.of());
        when(repository.findByClassName("default", "cliente-ws", "EmpresaDTO"))
                .thenReturn(Optional.of(new SearchResult("EmpresaDTO.java", "public class EmpresaDTO {}", 1.0)));

        AgentToolExecutor executor = new AgentToolExecutor(
                mock(SearchService.class), repository,
                new BreakingChangeAnalyzer(), new AlertService(), mock(AuditService.class)
        );

        String result = executor.execute(
                "audit_dto",
                "{\"dtoName\": \"EmpresaDTO\"}",
                "default",
                "cliente-ws"
        );

        assertTrue(result.contains("ALTO"), "Esperava risco ALTO mas foi: " + result);
        assertTrue(result.contains("Contrato externo: SIM"), "Esperava contrato externo: " + result);
    }
}
