package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptAssembler {

    public String buildPrompt(String question, List<SearchResult> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Use the context below to answer the question.\n\n");

        for (SearchResult d : docs) {
            sb.append("Source: ").append(d.path()).append("\n");
            sb.append(d.content()).append("\n\n");
        }

        sb.append("Question: ").append(question);
        return sb.toString();
    }
}
