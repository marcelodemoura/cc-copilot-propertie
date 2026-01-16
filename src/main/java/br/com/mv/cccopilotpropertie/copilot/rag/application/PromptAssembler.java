package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptAssembler {

    public String build(String question, List<SearchResult> context) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                        Você é um copiloto técnico especializado em análise de código Java.
                
                        TAREFA:
                        - Analise o CÓDIGO fornecido abaixo.
                        - Identifique campos obrigatórios (ex: @NotNull, @NotBlank).
                        - Informe:
                          • Nome do DTO
                          • Campo
                          • Anotação
                          • Onde isso é validado (classe)
                        REGRAS:
                        - Use SOMENTE o código fornecido.
                        - Se não houver validação explícita, diga isso claramente.
                        - Não invente informações.
                
                
                        === CÓDIGO ===
                """);

        for (SearchResult r : context.stream().limit(5).toList()) {
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
