package br.com.mv.cccopilotpropertie.copilot.answer;

import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class AnswerBuilder {

    /* =========================
       Builders genéricos (mantém V2)
       ========================= */

    private String message;
    private double confidence = 1.0;
    private List<CopilotAnswer.Source> sources = List.of();

    public static AnswerBuilder create() {
        return new AnswerBuilder();
    }

    public AnswerBuilder message(String message) {
        this.message = message;
        return this;
    }

    public AnswerBuilder confidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public AnswerBuilder sources(List<CopilotAnswer.Source> sources) {
        this.sources = sources;
        return this;
    }

    public CopilotAnswer build() {
        return new CopilotAnswer(
                message,
                sources,
                confidence,
                null,
                null
        );
    }

    public static CopilotAnswer simple(String message) {
        return new CopilotAnswer(
                message,
                List.of(),
                1.0,
                null,
                null
        );
    }


    /* =========================
       🎯 V3 — Builders de domínio
       ========================= */

    public static CopilotAnswer breakingResult(
            ChangeSet change,
            BreakingAnalysisResult result
    ) {

        String nextStep =
                result.breakingType() == BreakingType.BREAKING
                        ? "👉 Próximo passo: isso quebra alguma API?"
                        : "👉 Próximo passo: isso impacta outro sistema?";

        return CopilotAnswer.simple(
                """
                        Análise de Breaking Change:
                        
                        • Elemento: %s
                        • Tipo: %s
                        • Classificação: %s
                        • Motivo: %s
                        • Versionar: %s
                        
                        %s
                        """.formatted(
                        change.elementName(),
                        change.type(),
                        result.breakingType(),
                        result.reason(),
                        result.requiresVersioning() ? "SIM" : "NÃO",
                        nextStep
                )
        );
    }

    public static CopilotAnswer fieldRemovalDetected(
            String field,
            List<CopilotAnswer.Source> sources
    ) {
        return new CopilotAnswer(
                "Campo `" + field + "` possui usos e pode gerar impacto.",
                sources,
                1.0,
                null,
                null
        );
    }

    public static CopilotAnswer noHttpImpact(String dto) {
        return CopilotAnswer.simple(
                "A alteração **não quebra contrato HTTP**. Nenhum endpoint REST utiliza o DTO `" + dto + "`."
        );
    }

    public static CopilotAnswer httpImpact(
            String dto,
            List<CopilotAnswer.Source> endpoints
    ) {
        return new CopilotAnswer(
                "⚠️ Quebra de contrato HTTP detectada para o DTO `" + dto + "`.",
                endpoints,
                1.0,
                null,
                null
        );
    }

    public static CopilotAnswer noExternalImpact(String dto) {
        return CopilotAnswer.simple(
                "O DTO `" + dto + "` não possui uso em outros sistemas."
        );
    }


}
