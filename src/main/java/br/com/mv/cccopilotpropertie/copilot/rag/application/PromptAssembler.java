package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptAssembler {

    public String build(String question, List<SearchResult> context) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
        Você é o CC Copilot.
        Você responde APENAS com base nas informações fornecidas abaixo.
        Se a resposta não estiver nos trechos, responda: "Não encontrei essa informação no sistema."

        === BASE DE CONHECIMENTO ===
        """);

        for (SearchResult r : context) {
            sb.append("- ").append(r.content()).append("\n");
        }

        sb.append("""
        ============================

        Pergunta:
        """).append(question);

        sb.append("\n\nResposta:");

        return sb.toString();
    }
}
