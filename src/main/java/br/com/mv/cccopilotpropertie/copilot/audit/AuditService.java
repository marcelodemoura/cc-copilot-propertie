package br.com.mv.cccopilotpropertie.copilot.audit;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

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
}
