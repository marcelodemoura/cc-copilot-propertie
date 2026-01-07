package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptAssembler {

    public String build(String question, List<SearchResult> context) {

        StringBuilder sb = new StringBuilder();
        sb.append("Você é um copiloto que responde SOMENTE usando o contexto abaixo.\n\n");

        for (SearchResult r : context) {
            sb.append("Fonte:\n");
            sb.append("Arquivo: ").append(r.path()).append("\n");
            sb.append(r.content()).append("\n---\n");

        }

        sb.append("\nPergunta: ").append(question);
        sb.append("\nResposta:");

        return sb.toString();
    }
}
