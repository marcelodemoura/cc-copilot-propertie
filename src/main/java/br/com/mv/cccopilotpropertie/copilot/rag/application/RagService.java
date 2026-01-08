package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import org.springframework.stereotype.Service;@Service
public class RagService {

    private final SearchService search;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;
    private final ConfidenceEvaluator confidence;

    public RagService(SearchService s, PromptAssembler p, AnswerService a, ConfidenceEvaluator c) {
        search = s;
        promptAssembler = p;
        answer = a;
        confidence = c;
    }

    public CopilotAnswer ask(String tenant, String kb, String question) {

        var context = search.search(tenant, kb, question, 5);
        var prompt = promptAssembler.build(question, context);
        var response = answer.ask(prompt);
        var score = confidence.score(question, response, context);

        return new CopilotAnswer(response, score);
    }
}


