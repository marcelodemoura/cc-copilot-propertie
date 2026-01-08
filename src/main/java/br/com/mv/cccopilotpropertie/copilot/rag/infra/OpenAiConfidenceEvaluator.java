package br.com.mv.cccopilotpropertie.copilot.rag.infra;

import br.com.mv.cccopilotpropertie.copilot.rag.application.ConfidenceEvaluator;
import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiConfidenceEvaluator implements ConfidenceEvaluator {

    private final LlmClient llm;

    public OpenAiConfidenceEvaluator(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public double score(String question, String answer, List<SearchResult> context) {

        StringBuilder sb = new StringBuilder("""
        Avalie de 0 a 1 o quanto a resposta está fundamentada EXCLUSIVAMENTE nos trechos abaixo.

        Pergunta:
        """).append(question).append("\n\nResposta:\n").append(answer)
                .append("\n\nTrechos:\n");

        for (SearchResult r : context) {
            sb.append("- ").append(r.content()).append("\n");
        }

        sb.append("\nRetorne apenas um número:");

        String result = llm.complete(sb.toString()).replace(",", ".");

        return Double.parseDouble(result.replaceAll("[^0-9.]", ""));
    }
}
