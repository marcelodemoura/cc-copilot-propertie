package br.com.mv.cccopilotpropertie.copilot.intent.handlers;

import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.answer.AnswerBuilder;
import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Order(3)
@Service
public class BreakingHandler implements CopilotIntentHandler {

    private final SearchRepository searchRepository;
    private final BreakingChangeAnalyzer analyzer;
    private final AnswerBuilder answerBuilder;


    public BreakingHandler(
            SearchRepository searchRepository,
            BreakingChangeAnalyzer analyzer, AnswerBuilder answerBuilder
    ) {
        this.searchRepository = searchRepository;
        this.analyzer = analyzer;
        this.answerBuilder = answerBuilder;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.BREAKING_CHANGE;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        if (state == null || state.getLastChange() == null) return false;

        String q = question.toLowerCase();

        // 🚫 NÃO responde perguntas de API / HTTP
        if (q.contains("api") || q.contains("endpoint") || q.contains("http")) {
            return false;
        }

        return q.contains("breaking")
                || q.contains("quebra")
                || q.contains("versionar");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        ChangeSet change = state.getLastChange();

        ImpactAnalysis impact =
                ImpactAnalysis.from(
                        tenantId,
                        knowledgeBase,
                        change,
                        searchRepository
                );

        BreakingAnalysisResult result =
                analyzer.analyze(change, impact);

        boolean isBreaking = result.breakingType() == BreakingType.BREAKING;

        String message = isBreaking
                ? """
                ❌ **Breaking change detectado**
                
                A alteração no elemento `%s` quebra compatibilidade com código existente.
                
                • Tipo de mudança: %s
                • Motivo: %s
                • Versionamento necessário: SIM
                
                👉 Próximo passo: *isso quebra alguma API?*
                """.formatted(
                change.elementName(),
                change.type(),
                result.reason()
        )
                : """
                ✅ **Nenhum breaking change detectado**
                
                A alteração no elemento `%s` não afeta contratos existentes.
                
                • Tipo de mudança: %s
                • Motivo: %s
                • Versionamento necessário: NÃO
                
                👉 Próximo passo: *impacta outro sistema?*
                """.formatted(
                change.elementName(),
                change.type(),
                result.reason()
        );

        return new CopilotAnswer(
                message,
                List.of(),
                1.0,
                null,
                null
        );
    }
}
