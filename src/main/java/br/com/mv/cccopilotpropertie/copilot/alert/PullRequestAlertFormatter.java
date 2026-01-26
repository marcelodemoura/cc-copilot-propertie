package br.com.mv.cccopilotpropertie.copilot.alert;

import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.springframework.stereotype.Component;

@Component
public class PullRequestAlertFormatter {

    public String format(AlertResult alert, DtoAuditResult audit) {

        StringBuilder sb = new StringBuilder();

        sb.append(icon(alert.level()))
                .append(" **ALERTA ")
                .append(alert.level())
                .append("**\n\n");

        sb.append("### ").append(alert.title()).append("\n\n");

        sb.append("- **DTO:** ").append(audit.dtoName()).append("\n");
        sb.append("- **Risco:** ").append(audit.riskLevel()).append("\n");

        if (audit.isContractDto()) {
            sb.append("- **Tipo:** DTO de contrato entre sistemas\n");
        }

        sb.append("\n**Descrição:**\n");
        sb.append(alert.message()).append("\n");

        if (!audit.recommendations().isEmpty()) {
            sb.append("\n**Recomendações:**\n");
            audit.recommendations().forEach(r ->
                    sb.append("- ").append(r).append("\n")
            );
        }

        return sb.toString();
    }

    private String icon(AlertLevel level) {
        return switch (level) {
            case CRITICAL -> "🚨";
            case WARNING  -> "⚠️";
            case INFO     -> "ℹ️";
        };
    }
}
