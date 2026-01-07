package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.application.SearchService;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final SearchService search;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;

    public RagService(SearchService search,
                      PromptAssembler promptAssembler,
                      AnswerService answer) {
        this.search = search;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
    }

    public String ask(String question) {
        var context = search.search(question, 5);
        var promptFinal = promptAssembler.build(question, context);
        return answer.answer(promptFinal);
    }
}


