package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final SearchService search;
    private final AnswerService answer;
    private final PromptAssembler prompt;

    public RagService(SearchService search, AnswerService answer, PromptAssembler prompt) {
        this.search = search;
        this.answer = answer;
        this.prompt = prompt;
    }

    public String ask(String question) {
        List<SearchResult> docs = search.search(question, 6);
        String promptFinal = prompt.buildPrompt(question, docs);
        return answer.ask(promptFinal);
    }
}


