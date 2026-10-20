package br.com.mv.cccopilotpropertie.copilot.audit;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingAnalysisResult;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.springframework.stereotype.Service;
@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    // ✅ Auditoria de uso de DTO (PASSO 11–14)
    public void record(
            String tenantId,
            String knowledgeBase,
            DtoAuditResult audit,
            AlertResult alert
    ) {
        repository.save(new AuditExecution(
                tenantId,
                knowledgeBase,
                audit.dtoName(),
                audit.riskLevel(),
                audit.isContractDto(),
                audit.hasExplicitValidation(),
                audit.usageCount(),
                alert != null ? alert.level().name() : null
        ));
    }

    // ✅ Auditoria de decisão arquitetural (PASSO 15)
    public void recordChangeDecision(
            String tenantId,
            String knowledgeBase,
            ChangeSet change,
            BreakingAnalysisResult result,
            AlertResult alert
    ) {
        String dtoName = (change != null && change.dtoName() != null && !change.dtoName().isBlank())
                ? change.dtoName()
                : (change != null && change.elementName() != null ? change.elementName() : "UNKNOWN");
        String riskLevel = result != null ? result.breakingType().name() : "NON_BREAKING";
        boolean contract = result != null && result.breakingType() != br.com.mv.cccopilotpropertie.copilot.breaking.BreakingType.NON_BREAKING;
        String alertLvl = (alert != null && alert.level() != null) ? alert.level().name() : null;

        repository.save(new AuditExecution(
                tenantId,
                knowledgeBase,
                dtoName,
                riskLevel,
                contract,
                false,
                1,
                alertLvl,
                "CHANGE_DECISION"
        ));
    }
}
