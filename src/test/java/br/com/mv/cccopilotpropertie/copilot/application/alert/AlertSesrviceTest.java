package br.com.mv.cccopilotpropertie.copilot.application.alert;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertServiceTest {

    private final AlertService alertService = new AlertService();

    @Test
    void deveGerarAlertaCriticoParaDtoContratoComRiscoAlto() {

        DtoAuditResult audit = new DtoAuditResult(
                "EmpresaDTO",
                "ALTO",
                true,
                true,
                false,
                10,
                List.of(),
                true
        );

        Optional<AlertResult> alert = alertService.evaluate(audit);

        assertTrue(alert.isPresent());
        assertEquals("CRITICAL", alert.get().level().name());
    }
}

