package br.com.mv.cccopilotpropertie.copilot.application;

import br.com.mv.cccopilotpropertie.embedding.OpenAiChatService;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import org.springframework.stereotype.Service;

@Service
public class CopilotService {


    private final SearchService search;
    private final OpenAiChatService llm;

    public CopilotService(SearchService s, OpenAiChatService llm) {
        this.search = s;
        this.llm = llm;
    }

    public String ask(String question) {
        var context = search.search(question, 6);
        return llm.ask(question, context);
    }
}
