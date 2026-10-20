package br.com.mv.cccopilotpropertie.copilot.agent;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AgentTools {

    @Tool("Lista todos os projetos cadastrados no workspace (nome, ID e caminho raiz) para permitir navegação, leitura e edição entre projetos.")
    public String list_projects() {
        return null;
    }

    @Tool("Verifica alterações recentes, arquivos modificados (git status), últimos commits (git log) e resumo de diffs no repositório Git de um projeto (ou 'ALL' para todos os projetos).")
    public String git_changes(
            @P(value = "Nome ou ID do projeto alvo (opcional; use 'ALL' para todos os projetos ou deixe vazio para o projeto ativo)", required = false) String project,
            @P(value = "Se true, inclui o diff detalhado das alterações atuais (padrão false)", required = false) Boolean includeDiff,
            @P(value = "Filtro opcional por termo no nome do arquivo ou classe (ex: 'EventoMonitorado')", required = false) String filter
    ) {
        return null;
    }

    @Tool("Busca trechos de código, configuração ou documentação relevantes em um projeto específico ou em todos ('ALL').")
    public String search_code(
            @P("Texto a buscar na base de conhecimento") String query,
            @P(value = "Número máximo de resultados (padrão 8)", required = false) Integer limit,
            @P(value = "Nome ou ID do projeto alvo (opcional; use 'ALL' para buscar em todos os projetos ou deixe vazio para o projeto atual)", required = false) String project
    ) {
        return null;
    }

    @Tool("Localiza a definição completa de um DTO pelo nome da classe.")
    public String find_dto_definition(
            @P("Nome exato da classe DTO") String className,
            @P(value = "Nome ou ID do projeto alvo (opcional)", required = false) String project
    ) {
        return null;
    }

    @Tool("Lista todos os arquivos que referenciam um DTO.")
    public String find_dto_usages(
            @P("Nome da classe DTO") String className,
            @P(value = "Nome ou ID do projeto alvo (opcional)", required = false) String project
    ) {
        return null;
    }

    @Tool("Lista os controllers/endpoints REST que usam um DTO.")
    public String find_endpoints_using_dto(
            @P("Nome da classe DTO") String dtoName,
            @P(value = "Nome ou ID do projeto alvo (opcional)", required = false) String project
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
            @P("Nome do DTO que contém o campo") String dtoName,
            @P(value = "Nome ou ID do projeto alvo (opcional)", required = false) String project
    ) {
        return null;
    }

    @Tool("Realiza auditoria completa de um DTO: risco, validações, uso em contratos, recomendações.")
    public String audit_dto(
            @P("Nome da classe DTO") String dtoName,
            @P(value = "Nome ou ID do projeto alvo (opcional)", required = false) String project
    ) {
        return null;
    }

    @Tool("Lê o conteúdo de um arquivo pelo caminho relativo (dentro do projeto) ou absoluto com paginação de linhas (startLine e endLine). Para arquivos grandes, retorna também um mapa das próximas seções.")
    public String read_file(
            @P("Caminho do arquivo a ser lido (ex: src/main/resources/static/index.html)") String path,
            @P(value = "Linha inicial opcional (1-indexado)", required = false) Integer startLine,
            @P(value = "Linha final opcional (inclusive)", required = false) Integer endLine,
            @P(value = "Nome ou ID do projeto onde está o arquivo (opcional, deixe vazio para usar o projeto ativo)", required = false) String project
    ) {
        return null;
    }

    @Tool("Lista os arquivos e subdiretórios em formato de árvore hierárquica do projeto ativo (ou de outro projeto se especificado), destacando entrypoints e arquivos de interface/frontend.")
    public String list_files(
            @P(value = "Subcaminho relativo ou pasta a listar (vazio para a raiz do projeto)", required = false) String path,
            @P(value = "Profundidade máxima da árvore (padrão 4, máximo 5)", required = false) Integer maxDepth,
            @P(value = "Nome ou ID do projeto a explorar (opcional, deixe vazio para o projeto ativo)", required = false) String project
    ) {
        return null;
    }

    @Tool("Modifica cirurgicamente um arquivo existente substituindo 'targetContent' por 'replacementContent'. Use APENAS quando o usuário pedir/autorizar alterar ou implementar código (nunca em pedidos exclusivos de análise/sugestão). Sempre leia o trecho antes com 'read_file'.")
    public String modify_file(
            @P("Caminho do arquivo relativo à raiz do projeto (ex: src/main/resources/static/index.html)") String path,
            @P("Trecho exato do código existente a ser substituído (copiado do 'read_file', sem os números de linha)") String targetContent,
            @P("Novo trecho de código completo que substituirá o trecho alvo") String replacementContent,
            @P(value = "Motivo ou justificativa técnica da alteração", required = false) String reason,
            @P(value = "Nome ou ID do projeto alvo onde o arquivo será modificado (opcional, deixe vazio para o projeto ativo)", required = false) String project
    ) {
        return null;
    }

    @Tool("Cria um novo arquivo de código ou sobrescreve integralmente um arquivo pequeno. Use APENAS quando o usuário pedir para criar/implementar código. NUNCA use write_file para atualizar parcialmente um arquivo grande existente (use 'modify_file').")
    public String write_file(
            @P("Caminho do arquivo relativo à raiz do projeto (ex: src/main/java/br/com/mv/NovoServico.java)") String path,
            @P("Conteúdo textual integral a ser gravado no arquivo") String content,
            @P(value = "Motivo técnico da criação ou alteração do arquivo", required = false) String reason,
            @P(value = "Nome ou ID do projeto alvo onde o arquivo será gravado (opcional, deixe vazio para o projeto ativo)", required = false) String project
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
