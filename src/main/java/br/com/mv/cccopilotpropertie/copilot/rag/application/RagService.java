package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.application.SearchService;
import org.springframework.stereotype.Service;
@Service
public class RagService {

    private final SearchService search;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;

    public RagService(SearchService s,
                      PromptAssembler p,
                      AnswerService a) {
        search = s;
        promptAssembler = p;
        answer = a;
    }

    public String ask(String tenantId,
                      String knowledgeBase,
                      String question) {

        var context = search.search(tenantId, knowledgeBase, question, 6);
        var prompt = promptAssembler.build(question, context);
        return answer.ask(prompt);
    }
}



