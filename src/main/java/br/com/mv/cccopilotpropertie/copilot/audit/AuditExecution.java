package br.com.mv.cccopilotpropertie.copilot.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "copilot_audit_execution")
public class AuditExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String knowledgeBase;

    private String dtoName;
    private String riskLevel;

    private boolean contractDto;
    private boolean explicitValidation;
    private int usageCount;

    private String alertLevel; // CRITICAL | WARNING | null

    private OffsetDateTime executedAt;

    protected AuditExecution() {}

    private String auditType; // USAGE | CHANGE_DECISION

    public AuditExecution(
            String tenantId,
            String knowledgeBase,
            String dtoName,
            String riskLevel,
            boolean contractDto,
            boolean explicitValidation,
            int usageCount,
            String alertLevel
    ) {
        this.tenantId = tenantId;
        this.knowledgeBase = knowledgeBase;
        this.dtoName = dtoName;
        this.riskLevel = riskLevel;
        this.contractDto = contractDto;
        this.explicitValidation = explicitValidation;
        this.usageCount = usageCount;
        this.alertLevel = alertLevel;
        this.executedAt = OffsetDateTime.now();
    }

    // getters somente (sem setters)
}
