package br.com.mv.cccopilotpropertie.copilot.breaking;

import org.springframework.stereotype.Service;
@Service
public class BreakingChangeAnalyzer {

    public BreakingAnalysisResult analyze(
            ChangeSet change,
            ImpactAnalysis impact
    ) {

        // 1️⃣ Regras de FIELD
        if (change.target() == ChangeTarget.FIELD) {
            return analyzeFieldChange(change, impact);
        }

        // 2️⃣ Regras de DTO (futuro)
        if (change.target() == ChangeTarget.DTO) {
            return analyzeDtoChange(change, impact);
        }

        // 3️⃣ Regras de ENDPOINT (futuro)
        if (change.target() == ChangeTarget.ENDPOINT) {
            return analyzeEndpointChange(change, impact);
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

        // 🔴 REGRA QUE VOCÊ PERGUNTOU — ENTRA AQUI
        if (change.type() == ChangeType.REMOVE
                && impact.internalUsage()) {

            return breaking(
                    "Remoção de campo utilizado no projeto",
                    true
            );
        }

        return nonBreaking("Alteração de campo sem impacto detectado");
    }

    // =====================================================
    // DTO (stub)
    // =====================================================
    private BreakingAnalysisResult analyzeDtoChange(
            ChangeSet change,
            ImpactAnalysis impact
    ) {
        return nonBreaking("Regras de DTO ainda não implementadas");
    }

    // =====================================================
    // ENDPOINT (stub)
    // =====================================================
    private BreakingAnalysisResult analyzeEndpointChange(
            ChangeSet change,
            ImpactAnalysis impact
    ) {
        return nonBreaking("Regras de endpoint ainda não implementadas");
    }

    // =====================================================
    // FACTORY METHODS
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

