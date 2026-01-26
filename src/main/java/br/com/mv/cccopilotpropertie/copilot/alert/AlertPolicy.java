package br.com.mv.cccopilotpropertie.copilot.alert;

import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.springframework.stereotype.Component;

@Component
public class AlertPolicy {

    public AlertLevel resolveLevel(DtoAuditResult audit) {

        if (audit.isContractDto() && "ALTO".equals(audit.riskLevel())) {
            return AlertLevel.CRITICAL;
        }

        if ("ALTO".equals(audit.riskLevel())) {
            return AlertLevel.WARNING;
        }

        if ("MÉDIO".equals(audit.riskLevel())) {
            return AlertLevel.INFO;
        }

        return null; // sem alerta
    }
}
