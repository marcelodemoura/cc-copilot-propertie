package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.search.application.ReRanker;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class OpenAiReRanker implements ReRanker {

    private final LlmClient llm;

    public OpenAiReRanker(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public List<SearchResult> rerank(String question, List<SearchResult> candidates, int topK) {

        StringBuilder prompt = new StringBuilder("""
        Você é um classificador semântico.
        Retorne APENAS os %d IDs mais relevantes para responder a pergunta.

        Pergunta:
        """.formatted(topK)).append(question).append("\n\nTrechos:\n");

        for (int i = 0; i < candidates.size(); i++) {
            prompt.append("[%d] %s\n".formatted(i, candidates.get(i).content()));
        }

        prompt.append("\nResposta (apenas números separados por vírgula):");

        String result = llm.complete(prompt.toString());

        return Arrays.stream(result.replaceAll("[^0-9,]", "").split(","))
                .map(Integer::parseInt)
                .filter(i -> i < candidates.size())
                .map(candidates::get)
                .toList();
    }
}
