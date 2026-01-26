package br.com.mv.cccopilotpropertie.copilot.application.policy;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.copilot.policy.PolicyDecision;
import br.com.mv.cccopilotpropertie.copilot.policy.PolicyService;
import br.com.mv.cccopilotpropertie.copilot.policy.ProjectPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PolicyServiceTest {

    private final PolicyService policyService = new PolicyService();

    @Test
    void deveBloquearDtoInternoVazando() {

        DtoAuditResult audit = new DtoAuditResult(
                "EmpresaDTO",
                "ALTO",
                true,   // usado em outros projetos (vazou)
                true,
                false,
                5,
                List.of(),
                false   // NÃO é contrato => interno
        );

        ProjectPolicy policy = new ProjectPolicy(
                true,   // forbidInternalLeak
                false,  // forbidContractWithoutValidation
                false   // failOnCritical
        );

        PolicyDecision decision =
                policyService.evaluate("cliente-ws", audit, policy);

        assertFalse(decision.allowed());
    }
}
