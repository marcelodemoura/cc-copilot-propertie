package br.com.mv.cccopilotpropertie.copilot.policy;

import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import org.springframework.stereotype.Service;

@Service
public class PolicyService {

    public PolicyDecision evaluate(
            String knowledgeBase,
            DtoAuditResult audit,
            ProjectPolicy policy
    ) {

        if (audit.isContractDto() && !policy.allowContractDto()) {
            return PolicyDecision.block(
                    "Uso de DTO de contrato não permitido neste projeto"
            );
        }

        if (!audit.isContractDto()
                && audit.usedInOtherProjects()
                && !policy.allowInternalDtoLeak()) {

            return PolicyDecision.block(
                    "DTO interno vazando para outros projetos"
            );
        }

        return PolicyDecision.allow();
    }
}
