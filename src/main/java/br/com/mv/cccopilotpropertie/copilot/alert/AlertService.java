package br.com.mv.cccopilotpropertie.copilot.alert;

import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingAnalysisResult;
import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingType;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AlertService {

    public Optional<AlertResult> evaluate(DtoAuditResult audit) {

        if (audit == null) {
            return Optional.empty();
        }

        // 🚨 DTO de contrato com risco ALTO → CRITICAL
        if (audit.isContractDto() && "ALTO".equals(audit.riskLevel())) {
            return Optional.of(new AlertResult(
                    AlertLevel.CRITICAL,
                    "DTO de contrato com risco ALTO",
                    buildMessage(audit)
            ));
        }

        // ⚠️ Risco ALTO (não contrato) → WARNING
        if ("ALTO".equals(audit.riskLevel())) {
            return Optional.of(new AlertResult(
                    AlertLevel.WARNING,
                    "DTO com risco ALTO",
                    buildMessage(audit)
            ));
        }

        // ℹ️ Risco MÉDIO → INFO
        if ("MÉDIO".equals(audit.riskLevel())) {
            return Optional.of(new AlertResult(
                    AlertLevel.INFO,
                    "DTO com risco MÉDIO",
                    buildMessage(audit)
            ));
        }

        return Optional.empty();
    }

    private String buildMessage(DtoAuditResult audit) {
        return "O DTO " + audit.dtoName() +
                " apresenta risco " + audit.riskLevel() +
                (audit.isContractDto()
                        ? " e é utilizado como contrato entre sistemas."
                        : ".");
    }

    public Optional<AlertResult> evaluateBreaking(
            BreakingAnalysisResult result
    ) {
        if (result == null) return Optional.empty();

        if (result.breakingType() == BreakingType.BREAKING_CRITICAL) {
            return Optional.of(new AlertResult(
                    AlertLevel.CRITICAL,
                    "Breaking change crítico detectado",
                    result.reason()
            ));
        }

        if (result.breakingType() == BreakingType.BREAKING) {
            return Optional.of(new AlertResult(
                    AlertLevel.WARNING,
                    "Breaking change detectado",
                    result.reason()
            ));
        }

        return Optional.empty();
    }

}
