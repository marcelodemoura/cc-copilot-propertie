package br.com.mv.cccopilotpropertie.copilot.breaking;

import org.springframework.stereotype.Service;

@Service
public class BreakingChangeAnalyzer {

    public BreakingAnalysisResult analyze(
            ChangeSet change,
            ImpactAnalysis impact
    ) {
        if (change == null) {
            return nonBreaking("Nenhuma alteração informada");
        }

        if (change.target() == ChangeTarget.FIELD) {
            return analyzeFieldChange(change, impact);
        } else if (change.target() == ChangeTarget.DTO) {
            return analyzeDtoChange(change, impact);
        } else if (change.target() == ChangeTarget.ENDPOINT) {
            return analyzeEndpointChange(change, impact);
        }

        return nonBreaking("Nenhuma regra aplicável para alvo: " + change.target());
    }

    // =====================================================
    // FIELD
    // =====================================================
    private BreakingAnalysisResult analyzeFieldChange(
            ChangeSet change,
            ImpactAnalysis impact
    ) {
        if (change.type() == ChangeType.REMOVE) {
            if (impact.breaksHttpContract()) {
                return breaking("Remoção de campo quebra contrato HTTP", true);
            }
            if (impact.internalUsage()) {
                return breaking("Remoção de campo utilizado internamente", false);
            }
            return nonBreaking("Remoção de campo sem impacto detectado");
        }

        if (change.type() == ChangeType.RENAME) {
            if (impact.breaksHttpContract()) {
                return breaking("Renomeação de campo quebra contrato HTTP", true);
            }
            if (impact.internalUsage()) {
                return breaking("Renomeação de campo utilizado internamente", false);
            }
            return nonBreaking("Renomeação de campo sem impacto detectado");
        }

        if (change.type() == ChangeType.TYPE_CHANGE) {
            if (impact.breaksHttpContract()) {
                return breaking("Alteração de tipo de campo quebra contrato HTTP", true);
            }
            if (impact.internalUsage()) {
                return breaking("Alteração de tipo de campo afeta uso interno", false);
            }
            return nonBreaking("Alteração de tipo de campo sem impacto detectado");
        }

        if (change.type() == ChangeType.REQUIRED_CHANGE) {
            if (impact.breaksHttpContract()) {
                return breaking("Tornar campo obrigatório (@NotNull/@NotBlank) quebra contrato HTTP para clientes existentes", true);
            }
            if (impact.internalUsage()) {
                return breaking("Tornar campo obrigatório afeta uso interno", false);
            }
            return nonBreaking("Alteração de obrigatoriedade sem impacto detectado");
        }

        if (change.type() == ChangeType.ADD) {
            boolean isMandatory = isMandatoryState(change.afterState());
            if (isMandatory) {
                if (impact.breaksHttpContract()) {
                    return breaking("Adição de campo obrigatório (@NotNull/@NotBlank) em DTO exposto quebra contrato HTTP", true);
                }
                return breaking("Adição de campo obrigatório em DTO com uso interno", false);
            }
            return nonBreaking("Adição de campo opcional não quebra contratos existentes");
        }

        return nonBreaking("Alteração de campo sem impacto detectado");
    }

    // =====================================================
    // DTO
    // =====================================================
    private BreakingAnalysisResult analyzeDtoChange(ChangeSet change, ImpactAnalysis impact) {
        if (change.type() == ChangeType.REMOVE || change.type() == ChangeType.RENAME) {
            if (impact.breaksHttpContract()) {
                return breaking("Remoção ou renomeação de DTO de contrato HTTP", true);
            }
            if (impact.internalUsage()) {
                return breaking("Remoção ou renomeação de DTO utilizado internamente", false);
            }
            return nonBreaking("DTO sem impacto detectado");
        }
        return nonBreaking("Alteração de DTO não quebra contratos existentes");
    }

    // =====================================================
    // ENDPOINT
    // =====================================================
    private BreakingAnalysisResult analyzeEndpointChange(ChangeSet change, ImpactAnalysis impact) {
        if (change.type() == ChangeType.REMOVE || change.type() == ChangeType.RENAME || change.type() == ChangeType.TYPE_CHANGE) {
            return breaking("Alteração ou remoção de endpoint HTTP quebra consumidores externos", true);
        }
        return nonBreaking("Adição de endpoint não quebra consumidores existentes");
    }

    private boolean isMandatoryState(Object state) {
        if (state == null) return false;
        String s = state.toString().toLowerCase();
        return s.contains("@notnull") || s.contains("@notblank") || s.contains("@notempty")
                || s.contains("required") || s.contains("obrigatório") || s.contains("obrigatorio");
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
