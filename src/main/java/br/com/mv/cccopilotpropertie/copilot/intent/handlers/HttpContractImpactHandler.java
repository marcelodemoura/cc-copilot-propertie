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
@Order(2)
public class HttpContractImpactHandler implements CopilotIntentHandler {

    private final SearchRepository searchRepository;

    public HttpContractImpactHandler(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Override
    public CopilotIntent intent() {
        return CopilotIntent.HTTP_CONTRACT_IMPACT;
    }

    @Override
    public boolean supports(String question, ConversationState state) {
        if (state == null || state.getLastChange() == null) {
            return false;
        }

        String q = question.toLowerCase();
        return q.contains("api")
                || q.contains("endpoint")
                || q.contains("http")
                || q.contains("contrato");
    }

    @Override
    public CopilotAnswer handle(
            String tenantId,
            String knowledgeBase,
            String question,
            ConversationState state
    ) {

        ChangeSet change = state.getLastChange();
        String dto = change.dtoName();

        if (dto == null) {
            return new CopilotAnswer(
                    """
                    Ainda não consegui identificar o DTO afetado pela mudança.
                    
                    👉 Exemplo:
                    "posso remover o campo cnpj?"
                    """,
                    List.of(),
                    0.5,
                    null,
                    null
            );
        }

        List<SearchResult> endpoints =
                searchRepository.findEndpointsUsingDto(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        boolean breaksHttp = !endpoints.isEmpty();

        String message = breaksHttp
                ? """
                  ⚠️ **Quebra de contrato HTTP detectada**
                  
                  O DTO `%s` é utilizado por endpoints REST.
                  
                  • Mudança analisada: %s
                  • Tipo: %s
                  • Impacto: endpoints existentes dependem desse contrato
                  
                  Endpoints afetados:
                  %s
                  
                  👉 Próximo passo: *isso impacta outro sistema?*
                  """.formatted(
                dto,
                change.elementName(),
                change.type(),
                endpoints.stream()
                        .map(e -> "- " + e.path())
                        .distinct()
                        .reduce("", String::concat)
        )
                : """
                  ✅ **Nenhuma quebra de contrato HTTP**
                  
                  O DTO `%s` **não é utilizado** diretamente por endpoints REST.
                  
                  • Mudança analisada: %s
                  • Tipo: %s
                  • Impacto em API: NÃO
                  
                  👉 Próximo passo: *isso impacta outro sistema?*
                  """.formatted(
                dto,
                change.elementName(),
                change.type()
        );

        return new CopilotAnswer(
                message,
                endpoints.stream()
                        .map(e -> new CopilotAnswer.Source(e.path(), e.score()))
                        .toList(),
                1.0,
                null,
                null
        );
    }
}