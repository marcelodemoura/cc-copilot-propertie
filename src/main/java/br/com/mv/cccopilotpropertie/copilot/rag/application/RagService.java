package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.application.SearchService;
import org.springframework.stereotype.Service;
@Service
public class RagService {

    private final SearchService search;
    private final PromptAssembler prompt;
    private final AnswerService answer;

    public RagService(SearchService search, PromptAssembler prompt, AnswerService answer) {
        this.search = search;
        this.prompt = prompt;
        this.answer = answer;
    }

    public String ask(String tenantId, String knowledgeBase, String question) {

        var context = search.search(tenantId, knowledgeBase, question, 6);

        var finalPrompt = prompt.build(question, context);

        return answer.ask(finalPrompt);
    }
}

