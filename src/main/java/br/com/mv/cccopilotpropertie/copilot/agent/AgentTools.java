package br.com.mv.cccopilotpropertie.copilot.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AgentTools {

    public List<Map<String, Object>> all() {
        return List.of(
                tool("search_code",
                        "Busca trechos de código, configuração ou documentação relevantes para a pergunta.",
                        Map.of(
                                "query", param("string", "Texto a buscar na base de conhecimento"),
                                "limit", param("integer", "Número máximo de resultados (padrão 12)")
                        ),
                        List.of("query")
                ),
                tool("find_dto_definition",
                        "Localiza a definição completa de um DTO pelo nome da classe.",
                        Map.of("className", param("string", "Nome exato da classe DTO")),
                        List.of("className")
                ),
                tool("find_dto_usages",
                        "Lista todos os arquivos que referenciam um DTO.",
                        Map.of("className", param("string", "Nome da classe DTO")),
                        List.of("className")
                ),
                tool("find_endpoints_using_dto",
                        "Lista os controllers/endpoints REST que usam um DTO.",
                        Map.of("dtoName", param("string", "Nome da classe DTO")),
                        List.of("dtoName")
                ),
                tool("find_external_usages",
                        "Verifica se um DTO é usado em outras bases de conhecimento (outros projetos/sistemas).",
                        Map.of("dtoName", param("string", "Nome da classe DTO")),
                        List.of("dtoName")
                ),
                tool("analyze_breaking_change",
                        "Analisa se a remoção ou alteração de um campo em um DTO é uma breaking change.",
                        Map.of(
                                "fieldName", param("string", "Nome do campo"),
                                "dtoName", param("string", "Nome do DTO que contém o campo")
                        ),
                        List.of("fieldName", "dtoName")
                ),
                tool("audit_dto",
                        "Realiza auditoria completa de um DTO: risco, validações, uso em contratos, recomendações.",
                        Map.of("dtoName", param("string", "Nome da classe DTO")),
                        List.of("dtoName")
                )
        );
    }

    private Map<String, Object> tool(String name, String description,
                                     Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", required
                        )
                )
        );
    }

    private Map<String, Object> param(String type, String description) {
        return Map.of("type", type, "description", description);
    }
}
