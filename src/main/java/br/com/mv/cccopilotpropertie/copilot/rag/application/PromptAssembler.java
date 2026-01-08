package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptAssembler {

    public String build(String question, List<SearchResult> context) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
        Você é o CC Copilot, um assistant iconic do Command Center MV.

        REGRAS:
        - Responda APENAS com base no conteúdo fornecido em CONTEXTO.
        - Se a informação não estiver no contexto, responda exatamente:
          "Não encontrei essa informação na base de conhecimento."
        - Não invente, não deduza, não presuma.

        Ao final da resposta, cite as fontes no formato:
        Fonte: <path>

        =================== CONTEXTO ===================
        """);

        for (SearchResult r : context) {
            sb.append("""
            ---
            FONTE: %s
            %s
            """.formatted(r.path(), r.content()));
        }

        sb.append("""
        =================================================

        Pergunta:
        """).append(question);

        sb.append("\n\nResposta:");

        return sb.toString();
    }
}
