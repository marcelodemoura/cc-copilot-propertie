package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final SearchService search;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;

    public RagService(
            SearchService search,
            PromptAssembler promptAssembler,
            AnswerService answer
    ) {
        this.search = search;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
    }

    public CopilotAnswer ask(
            String tenantId,
            String knowledgeBase,
            String question
    ) {
        var docs = search.search(tenantId, knowledgeBase, question, 12);

        double confidence = calculateConfidence(docs);

        if (docs.isEmpty() || confidence < 0.15) {
            return new CopilotAnswer(
                    "Não encontrei informações suficientes na base de conhecimento para responder essa pergunta.",
                    List.of(),
                    confidence
            );
        }

        var prompt = promptAssembler.build(question, docs);
        var response = answer.ask(prompt);

        var sources = docs.stream()
                .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                .toList();

        return new CopilotAnswer(
                response,
                sources,
                confidence
        );
    }

    private double calculateConfidence(List<SearchResult> docs) {
        if (docs == null || docs.isEmpty()) {
            return 0.0;
        }

        return docs.stream()
                .limit(3)
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0.0);
    }
}