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
                        - Responda EXATAMENTE à PERGUNTA feita pelo usuário.
                
                        REGRAS GERAIS:
                        - Use SOMENTE o código fornecido no CONTEXTO.
                        - Não invente informações.
                        - Se a informação não existir no código, diga claramente:
                          "Não encontrei essa informação na base de conhecimento."
                
                        REGRAS PARA VALIDAÇÃO (quando a pergunta for sobre campos obrigatórios):
                        - Identifique campos anotados com @NotNull, @NotBlank ou similares.
                        - Informe:
                          • Nome do DTO
                          • Campo
                          • Anotação
                          • Onde isso é validado (classe onde a anotação está)
                
                        REGRAS PARA USO DO DTO (quando a pergunta for sobre uso):
                        - "Uso do DTO" significa referências externas ao DTO.
                        - Não considere o próprio DTO nem sua herança direta como uso.
                        - Classifique os usos por tipo:
                          • Controller
                          • Service
                          • DTO dependente
                          • Mensageria
                        - Liste apenas os usos que aparecem no código fornecido.
                

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
