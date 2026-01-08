package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.application.CopilotHistoryService;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final SearchService search;
    private final PromptAssembler prompt;
    private final AnswerService answer;
    private final CopilotHistoryService history;

    public RagService(SearchService s, PromptAssembler p, AnswerService a, CopilotHistoryService history) {
        search = s;
        prompt = p;
        answer = a;
        this.history = history;
    }

    public CopilotAnswer ask(
            String tenant,
            String knowledgeBase,
            String question
    ) {

        var context = search.search(tenant, knowledgeBase, question, 6);

        var promptFinal = prompt.build(question, context);

        var response = answer.ask(promptFinal);

        double confidence = context.stream()
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0);

        var sources = context.stream()
                .map(r -> new CopilotAnswer.Source(r.path(), r.score()))
                .toList();

        var copilotAnswer = new CopilotAnswer(response, sources, confidence);

        // 👇 AQUI entra o histórico
        history.save(tenant, knowledgeBase, question, copilotAnswer);

        return copilotAnswer;
    }
}
