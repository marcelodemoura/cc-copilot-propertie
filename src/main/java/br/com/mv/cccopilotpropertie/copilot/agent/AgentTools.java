package br.com.mv.cccopilotpropertie.copilot.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AgentTools {

    @Tool("Busca trechos de código, configuração ou documentação relevantes para a pergunta.")
    public String search_code(
            @P("Texto a buscar na base de conhecimento") String query,
            @P(value = "Número máximo de resultados (padrão 12)", required = false) Integer limit
    ) {
        return null;
    }

    @Tool("Localiza a definição completa de um DTO pelo nome da classe.")
    public String find_dto_definition(
            @P("Nome exato da classe DTO") String className
    ) {
        return null;
    }

    @Tool("Lista todos os arquivos que referenciam um DTO.")
    public String find_dto_usages(
            @P("Nome da classe DTO") String className
    ) {
        return null;
    }

    @Tool("Lista os controllers/endpoints REST que usam um DTO.")
    public String find_endpoints_using_dto(
            @P("Nome da classe DTO") String dtoName
    ) {
        return null;
    }

    @Tool("Verifica se um DTO é usado em outras bases de conhecimento (outros projetos/sistemas).")
    public String find_external_usages(
            @P("Nome da classe DTO") String dtoName
    ) {
        return null;
    }

    @Tool("Analisa se a remoção ou alteração de um campo em um DTO é uma breaking change.")
    public String analyze_breaking_change(
            @P("Nome do campo") String fieldName,
            @P("Nome do DTO que contém o campo") String dtoName
    ) {
        return null;
    }

    @Tool("Realiza auditoria completa de um DTO: risco, validações, uso em contratos, recomendações.")
    public String audit_dto(
            @P("Nome da classe DTO") String dtoName
    ) {
        return null;
    }

    @SuppressWarnings("deprecation")
    public List<Map<String, Object>> all() {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(this);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ToolSpecification spec : specs) {
            Map<String, Object> fn = new HashMap<>();
            fn.put("name", spec.name());
            fn.put("description", spec.description());

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("type", "object");

            if (spec.toolParameters() != null) {
                parameters.put("properties", spec.toolParameters().properties() != null
                        ? spec.toolParameters().properties()
                        : Map.of());
                if (spec.toolParameters().required() != null && !spec.toolParameters().required().isEmpty()) {
                    parameters.put("required", spec.toolParameters().required());
                }
            } else {
                parameters.put("properties", Map.of());
            }

            fn.put("parameters", parameters);
            result.add(Map.of("type", "function", "function", fn));
        }

        return result;
    }
}
