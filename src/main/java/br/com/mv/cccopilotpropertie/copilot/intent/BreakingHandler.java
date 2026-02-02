package br.com.mv.cccopilotpropertie.copilot.intent;

import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingAnalysisResult;
import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingChangeAnalyzer;
import br.com.mv.cccopilotpropertie.copilot.breaking.ImpactAnalysis;
import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BreakingHandler implements CopilotIntentHandler {

    private final BreakingChangeAnalyzer analyzer;
    private final SearchRepository repository;

    public BreakingHandler(
            BreakingChangeAnalyzer analyzer,
            SearchRepository repository
    ) {
        this.analyzer = analyzer;
        this.repository = repository;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        return question.toLowerCase().contains("breaking")
                && state.hasChange();
    }

    @Override
    public CopilotIntent intent() {
        return null;
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        ImpactAnalysis impact =
                ImpactAnalysis.from(
                        tenantId,
                        knowledgeBase,
                        state.getLastChange(),
                        repository
                );

        BreakingAnalysisResult result =
                analyzer.analyze(state.getLastChange(), impact);

        return new CopilotAnswer(
                """
                        Análise de Breaking Change:
                        • Elemento: %s
                        • Classificação: %s
                        • Motivo: %s
                        • Versionar: %s
                        """.formatted(
                        state.getLastChange().elementName(),
                        result.breakingType(),
                        result.reason(),
                        result.requiresVersioning() ? "SIM" : "NÃO"
                ),
                List.of(),
                1.0,
                null,
                null
        );
    }
}
