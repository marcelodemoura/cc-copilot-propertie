package br.com.mv.cccopilotpropertie.copilot.intent.handlers;

import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeSet;
import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntentHandler;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Order(3)
public class ExternalImpactHandler implements CopilotIntentHandler {

    private final SearchRepository searchRepository;

    public ExternalImpactHandler(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.EXTERNAL_IMPACT;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        String q = question.toLowerCase();
        return q.contains("outro sistema")
                || q.contains("externo")
                || q.contains("contrato externo");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        if (state == null || state.getLastChange() == null) {
            return new CopilotAnswer(
                    """
                    Ainda não sei qual mudança você está analisando.
                    
                    👉 Exemplo:
                    "posso remover o campo cnpj?"
                    """,
                    List.of(),
                    0.5,
                    null,
                    null
            );
        }

        ChangeSet change = state.getLastChange();
        String dto = change.dtoName();

        if (dto == null) {
            return new CopilotAnswer(
                    """
                    Não consegui identificar o DTO afetado pela mudança.
                    
                    👉 Dica:
                    pergunte primeiro sobre a remoção do campo.
                    """,
                    List.of(),
                    0.5,
                    null,
                    null
            );
        }

        List<SearchResult> externalUsages =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        boolean hasExternalImpact = !externalUsages.isEmpty();

        String message = hasExternalImpact
                ? """
                  ⚠️ **Impacto externo detectado**
                  
                  O DTO `%s` é utilizado por outros sistemas.
                  
                  • Mudança analisada: %s
                  • Tipo: %s
                  • Risco: quebra de contrato entre sistemas
                  
                  Sistemas afetados:
                  %s
                  
                  👉 Próximo passo: *preciso versionar essa mudança?*
                  """.formatted(
                dto,
                change.elementName(),
                change.type(),
                externalUsages.stream()
                        .map(r -> "- " + r.path())
                        .distinct()
                        .reduce("", String::concat)
        )
                : """
                  ✅ **Nenhum impacto externo detectado**
                  
                  O DTO `%s` **não é utilizado** por outros sistemas.
                  
                  • Mudança analisada: %s
                  • Tipo: %s
                  • Impacto externo: NÃO
                  
                  👉 Próximo passo: *isso quebra alguma API?*
                  """.formatted(
                dto,
                change.elementName(),
                change.type()
        );

        return new CopilotAnswer(
                message,
                externalUsages.stream()
                        .map(r -> new CopilotAnswer.Source(r.path(), r.score()))
                        .toList(),
                1.0,
                null,
                null
        );
    }
}
