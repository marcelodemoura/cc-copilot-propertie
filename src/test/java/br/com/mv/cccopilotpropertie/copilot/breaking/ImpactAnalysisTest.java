package br.com.mv.cccopilotpropertie.copilot.breaking;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImpactAnalysisTest {

    @Test
    void shouldDetectBreaksHttpContractEvenWithoutInternalUsage() {
        SearchRepository repository = mock(SearchRepository.class);

        // DTO é exposto em um controller
        when(repository.findEndpointsUsingDto("tenant-1", "kb-1", "UserDto"))
                .thenReturn(List.of(new SearchResult("UserController.java", "@PostMapping public void create(UserDto dto)", 1.0)));
        // Não há outros usos no repo
        when(repository.findUsagesInOtherKnowledgeBases("tenant-1", "kb-1", "UserDto"))
                .thenReturn(List.of());
        when(repository.findUsagesByClassName("tenant-1", "kb-1", "email"))
                .thenReturn(List.of());

        ChangeSet change = new ChangeSet(ChangeTarget.FIELD, ChangeType.REMOVE, "email", "UserDto", null, null);

        ImpactAnalysis impact = ImpactAnalysis.from("tenant-1", "kb-1", change, repository);

        assertFalse(impact.internalUsage(), "Não deve haver uso interno");
        assertTrue(impact.breaksHttpContract(), "Deve quebrar contrato HTTP porque o DTO está exposto em endpoint!");
    }

    @Test
    void shouldDetectInternalUsageViaGettersSettersAndDirectAccess() {
        SearchRepository repository = mock(SearchRepository.class);

        when(repository.findEndpointsUsingDto("tenant-1", "kb-1", "OrderDto"))
                .thenReturn(List.of());
        when(repository.findUsagesInOtherKnowledgeBases("tenant-1", "kb-1", "OrderDto"))
                .thenReturn(List.of());

        SearchResult codeWithGetter = new SearchResult("OrderService.java", "Long id = order.getId();", 1.0);
        when(repository.findUsagesByClassName("tenant-1", "kb-1", "id"))
                .thenReturn(List.of(codeWithGetter));

        ChangeSet change = new ChangeSet(ChangeTarget.FIELD, ChangeType.REMOVE, "id", "OrderDto", null, null);

        ImpactAnalysis impact = ImpactAnalysis.from("tenant-1", "kb-1", change, repository);

        assertTrue(impact.internalUsage(), "Deve detectar uso interno através de getter getId()");
        assertFalse(impact.breaksHttpContract(), "Não exposto em endpoint");
    }
}
