package br.com.mv.cccopilotpropertie.copilot.application.rag;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.copilot.rag.application.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RagServiceTest {

    @Autowired
    RagService ragService;

    @Test
    void deveGerarRiscoAltoParaDtoDeContratoSemValidacao() {

        CopilotAnswer answer = ragService.ask(
                "default",
                "cliente-ws",
                "Existe risco no uso do Empresa DTO?"
        );

        assertNotNull(answer.structured());
        assertTrue(answer.structured() instanceof DtoAuditResult);

        DtoAuditResult audit = (DtoAuditResult) answer.structured();

        assertEquals("ALTO", audit.riskLevel());
        assertTrue(audit.isContractDto());

    }
}

