package br.com.mv.cccopilotpropertie.copilot.history;

import br.com.mv.cccopilotpropertie.copilot.history.api.CopilotHistoryController;
import br.com.mv.cccopilotpropertie.copilot.history.api.CopilotHistoryResponse;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.copilot.history.domain.CopilotInteractionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CopilotHistoryControllerArchitectureTest {

    @Test
    void controllerShouldDelegateToHistoryService() {
        CopilotHistoryService service = mock(CopilotHistoryService.class);
        CopilotHistoryController controller = new CopilotHistoryController(service);

        CopilotInteractionEntity entity = new CopilotInteractionEntity(
                "tenant-1", "kb-1", "session-1", "Qual classe?", "Resposta da classe", 0.95
        );
        Page<CopilotInteractionEntity> page = new PageImpl<>(List.of(entity));

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(service.list("tenant-1", "kb-1", pageRequest)).thenReturn(page);

        Page<CopilotHistoryResponse> response = controller.list("tenant-1", "kb-1", pageRequest);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("tenant-1", response.getContent().get(0).tenantId());
        assertEquals("Qual classe?", response.getContent().get(0).question());

        verify(service).list("tenant-1", "kb-1", pageRequest);
    }
}
