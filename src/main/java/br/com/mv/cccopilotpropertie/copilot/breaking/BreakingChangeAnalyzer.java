package br.com.mv.cccopilotpropertie.copilot.breaking;

import org.springframework.stereotype.Service;

@Service
public class BreakingChangeAnalyzer {

    public BreakingAnalysisResult analyze(
            ChangeSet change,
            ImpactAnalysis impact
    ) {

        if (change.target() == ChangeTarget.FIELD) {
            return analyzeFieldChange(change, impact);
        }

        return nonBreaking("Nenhuma regra aplicável");
    }

    // =====================================================
    // FIELD
    // =====================================================
    private BreakingAnalysisResult analyzeFieldChange(
            ChangeSet change,
            ImpactAnalysis impact
    ) {

        if (change.type() == ChangeType.REMOVE
                && impact.internalUsage()) {

            if (impact.breaksHttpContract()) {
                return breaking(
                        "Remoção de campo quebra contrato HTTP",
                        true
                );
            }

            return breaking(
                    "Remoção de campo utilizado internamente",
                    true
            );
        }

        return nonBreaking("Alteração de campo sem impacto detectado");
    }

    // =====================================================
    // FACTORY
    // =====================================================
    private BreakingAnalysisResult breaking(
            String reason,
            boolean requiresVersioning
    ) {
        return new BreakingAnalysisResult(
                BreakingType.BREAKING,
                reason,
                requiresVersioning
        );
    }

    private BreakingAnalysisResult nonBreaking(String reason) {
        return new BreakingAnalysisResult(
                BreakingType.NON_BREAKING,
                reason,
                false
        );
    }
}
