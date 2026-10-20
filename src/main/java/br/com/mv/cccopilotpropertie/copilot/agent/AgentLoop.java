package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.infra.CopilotInteractionRepository;
import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentLoop {

    private static final int MAX_ITERATIONS = 12;

    private static final String SYSTEM_PROMPT = """
            Você é um engenheiro de software e copiloto autônomo poliglota (Java, Kotlin, TypeScript/JavaScript, Node.js, React, Angular, Vue, Python, Go, C#, Docker/Infra) e especialista em bancos de dados (Oracle SQL/PL-SQL, PostgreSQL, SQL Server/T-SQL, MySQL, MongoDB, Redis), capaz de trabalhar em múltiplos projetos simultaneamente.
            Você possui ferramentas para listar projetos ('list_projects'), inspecionar alterações Git ('git_changes'), buscar código ('search_code'), ler/listar arquivos ('read_file', 'list_files'), auditar contratos/DTOs/schemas e editar/criar arquivos ('modify_file', 'write_file') tanto no projeto ativo quanto em outros projetos do workspace (usando o parâmetro opcional 'project').

            POSTURA PROATIVA E AUTÔNOMA (REGRA #1 - CRÍTICA):
            - AGIR PRIMEIRO, NUNCA ENROLAR COM PERGUNTAS: É ESTRITAMENTE PROIBIDO responder devolvendo menus de opções ("Você quer 1 ou 2?", "Responda A, B ou C", "Qual o valor de N/K?", "Quer usar Git ou timestamps?") antes de chamar ferramentas.
            - Na iteração 1, se o usuário pedir para verificar, analisar, comparar ou alterar qualquer coisa em um ou mais projetos, CHAME AS FERRAMENTAS IMEDIATAMENTE assumindo o padrão mais inteligente.
            - Quando o usuário perguntar "quais classes/arquivos foram alterados" ou "o que mudou em X e Y", execute IMEDIATAMENTE a ferramenta 'git_changes' para cada projeto mencionado (ex: `git_changes(project="command-center-ws")` e `git_changes(project="command-center-view")`) e liste diretamente os arquivos modificados.

            REGRAS ESTRITAS DE FORMATAÇÃO E BREVIDADE:
            - RESPOSTAS CURTAS E CIRÚRGICAS: Responda em no máximo 1 a 3 parágrafos curtos ou bullet points diretos, salvo se o usuário pedir explicitamente um detalhamento longo.
            - PROIBIDO: Saudações, introduções ("Com base na análise...", "Analisei os arquivos..."), explicações óbvias do que o código faz linha a linha, resumos repetitivos no final ("Em resumo...", "Dessa forma...") ou seções como "Recomendações Adicionais" / "Próximos Passos" que não foram pedidas.
            - Se a pergunta for objetiva (ex: "onde fica X?", "qual o impacto de Y?", "gere uma query para Z"), responda direto com o fato ou com o bloco SQL/código enxuto.
            - Ao editar ou criar arquivos ('modify_file', 'write_file'), confirme em 1 frase quais arquivos foram alterados e mostre APENAS o bloco ```diff essencial. Não explique o código alterado a menos que seja complexo.
            - Ao sugerir código/queries sem aplicar, mostre apenas o snippet/diff relevante — nunca o arquivo inteiro.
            - Banco de Dados e Queries:
              * Identifique as tabelas/colunas reais lendo as entidades (@Entity/@Table, migrations Flyway/Liquibase, schemas Prisma/SQL ou packages PL/SQL) antes de escrever queries, evitando inventar nomes de colunas.
              * Respeite estritamente o dialeto do banco usado no projeto (ex: Oracle PL/SQL com sequences/packages/triggers .pks/.pkb/.prc/.trg, PostgreSQL, SQL Server, etc.).
            - Trabalho Entre Projetos e Stacks (Cross-Project):
              * Se a tarefa envolver múltiplos projetos (ex: Backend + Frontend + Scripts de Banco), passe o nome da pasta ou ID no parâmetro 'project' de 'git_changes', 'search_code', 'read_file', 'list_files', 'modify_file' ou 'write_file' (funciona mesmo para pastas no workspace que ainda não estejam indexadas no banco).
              * Você pode ler um contrato/entidade/package em um projeto A e criar/atualizar a classe, query ou componente correspondente no projeto B na mesma execução.
            - Quando perguntado sobre a arquitetura de um projeto: leia o manifesto da stack (package.json, build.gradle, pom.xml, requirements.txt, pyproject.toml, go.mod, etc.) ou README.md, identifique os entrypoints/rotas/componentes principais e resuma em no máximo 3 tópicos curtos (Propósito, Stack, Módulos).
            """;

    private final LlmClient llm;
    private final AgentTools tools;
    private final AgentToolExecutor executor;
    private final CopilotInteractionRepository historyRepo;
    private final ProjectRepository projectRepo;

    public AgentLoop(LlmClient llm, AgentTools tools, AgentToolExecutor executor,
                     CopilotInteractionRepository historyRepo) {
        this(llm, tools, executor, historyRepo, null);
    }

    @Autowired
    public AgentLoop(LlmClient llm, AgentTools tools, AgentToolExecutor executor,
                     CopilotInteractionRepository historyRepo,
                     @Autowired(required = false) ProjectRepository projectRepo) {
        this.llm = llm;
        this.tools = tools;
        this.executor = executor;
        this.historyRepo = historyRepo;
        this.projectRepo = projectRepo;
    }

    public CopilotAnswer run(String tenantId, String kb, String question, String sessionId) {
        return run(tenantId, kb, question, sessionId, null);
    }

    public CopilotAnswer run(String tenantId, String kb, String question, String sessionId, AgentProgressListener listener) {

        if (listener != null) {
            listener.onProgress("status", "Analisando pergunta e contexto recente...");
        }

        String effectiveSystemPrompt = buildSystemPrompt(tenantId, kb);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", effectiveSystemPrompt));

        // injeta histórico recente da sessão para manter contexto
        loadHistory(tenantId, kb, sessionId, messages);

        messages.add(Map.of("role", "user", "content", question));

        List<CopilotAnswer.Source> sources = new ArrayList<>();
        int consecutiveListFiles = 0;
        String lastToolCallSignature = null;
        int consecutiveSameCall = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (listener != null) {
                listener.onProgress("thinking", "Avaliando código e decidindo passos (iteração " + (i + 1) + ")...");
            }

            ToolCallResult result = llm.chat(messages, tools.all());

            if (!result.hasToolCalls()) {
                // LLM deu resposta final
                String text = result.text();
                String patch = extractPatch(text);
                String plan = extractPlan(text);
                CopilotAnswer finalAnswer = new CopilotAnswer(text, sources, 1.0, null, null, patch, plan);
                if (listener != null) {
                    listener.onProgress("answer", finalAnswer);
                }
                return finalAnswer;
            }

            if (listener != null) {
                var callsSummary = result.toolCalls().stream().map(tc -> Map.of(
                        "tool", tc.name(),
                        "arguments", tc.argumentsJson()
                )).toList();
                listener.onProgress("tool_calls", callsSummary);
            }

            // adiciona a mensagem do assistente com os tool_calls
            messages.add(assistantToolCallMessage(result.toolCalls()));

            // executa cada tool e devolve o resultado ao LLM
            for (ToolCallResult.ToolCall tc : result.toolCalls()) {
                if (listener != null) {
                    listener.onProgress("tool_start", "Executando ferramenta: " + tc.name());
                }

                String callSignature = tc.name() + ":" + (tc.argumentsJson() != null ? tc.argumentsJson().trim() : "");
                if (callSignature.equals(lastToolCallSignature)) {
                    consecutiveSameCall++;
                } else {
                    consecutiveSameCall = 1;
                    lastToolCallSignature = callSignature;
                }

                String toolResult = executor.execute(tc.name(), tc.argumentsJson(), tenantId, kb);

                if ("list_files".equals(tc.name())) {
                    consecutiveListFiles++;
                    if (consecutiveListFiles >= 2) {
                        toolResult += "\n[Dica de síntese: A estrutura de pastas principal já foi mapeada. Use 'search_code' para localizar @RestController/@SpringBootApplication ou elabore sua resposta com as informações já disponíveis.]";
                    }
                } else {
                    consecutiveListFiles = 0;
                }

                if (consecutiveSameCall >= 2) {
                    toolResult += "\n[Aviso do Sistema: Chamada repetida detectada com os mesmos parâmetros. Evite repetir esta ação e prossiga formulando sua análise com as informações obtidas.]";
                }

                // Limita payload excessivo de ferramentas para não estourar a janela de contexto do LLM
                if (toolResult != null && toolResult.length() > 8000) {
                    toolResult = toolResult.substring(0, 8000) + "\n... [trecho restante truncado para preservar a janela de contexto do LLM]";
                }

                // registra fontes para rastreabilidade
                extractSources(tc.name(), toolResult, sources);

                if (listener != null) {
                    listener.onProgress("tool_done", Map.of(
                            "tool", tc.name(),
                            "preview", toolResult.substring(0, Math.min(160, toolResult.length()))
                    ));
                }

                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", tc.id(),
                        "content", toolResult
                ));
            }
        }

        // Síntese final com o contexto acumulado caso o limite de iterações tenha sido atingido
        if (listener != null) {
            listener.onProgress("status", "Sintetizando resposta final com o contexto técnico coletado...");
        }

        messages.add(Map.of(
                "role", "user",
                "content", "Responda diretamente à pergunta do usuário em no máximo 3 parágrafos curtos ou bullet points objetivos, sem introduções ou conclusões genéricas."
        ));

        ToolCallResult finalAttempt = llm.chat(messages, List.of());
        String finalText = finalAttempt.text();
        if (finalText != null && !finalText.isBlank()) {
            String patch = extractPatch(finalText);
            String plan = extractPlan(finalText);
            CopilotAnswer synthesized = new CopilotAnswer(finalText, sources, 0.85, null, null, patch, plan);
            if (listener != null) {
                listener.onProgress("answer", synthesized);
            }
            return synthesized;
        }

        return new CopilotAnswer(
                "Não foi possível concluir a análise dentro do limite de iterações.",
                sources, 0.5, null, null, null, null
        );
    }

    private String buildSystemPrompt(String tenantId, String kb) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        if (projectRepo == null) {
            return sb.toString();
        }
        try {
            if (kb != null && !kb.isBlank()) {
                try {
                    UUID id = UUID.fromString(kb);
                    var pOpt = projectRepo.findById(id);
                    if (pOpt.isPresent()) {
                        var p = pOpt.get();
                        sb.append("\nProjeto Ativo (padrão quando 'project' não é informado):\n")
                                .append("- Nome: ").append(p.getName())
                                .append(" | ID: ").append(p.getId())
                                .append(" | Raiz: ").append(p.getRootPath()).append("\n");
                    }
                } catch (Exception ignored) {
                }
            }

            var allProjects = (tenantId != null && !tenantId.isBlank())
                    ? projectRepo.findByTenantId(tenantId)
                    : projectRepo.findAll();
            if (allProjects != null && !allProjects.isEmpty()) {
                sb.append("\nProjetos Disponíveis no Workspace (você pode ler, buscar e modificar qualquer um passando o nome ou ID no parâmetro 'project'):\n");
                for (var proj : allProjects) {
                    sb.append("- ").append(proj.getName())
                            .append(" (ID: ").append(proj.getId())
                            .append(", Path: ").append(proj.getRootPath()).append(")\n");
                }
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void loadHistory(String tenantId, String kb, String sessionId, List<Map<String, Object>> messages) {
        if (sessionId == null || sessionId.isBlank()) return;

        var history = historyRepo
                .findTop6ByTenantIdAndKnowledgeBaseAndSessionIdOrderByCreatedAtDesc(tenantId, kb, sessionId);

        Collections.reverse(history);

        history.forEach(h -> {
            String q = h.getQuestion() != null ? h.getQuestion() : "";
            String a = h.getAnswer() != null ? h.getAnswer() : "";
            if (a.length() > 400) {
                a = a.substring(0, 400) + "\n... [resumo do contexto anterior]";
            }
            messages.add(Map.of("role", "user", "content", q));
            messages.add(Map.of("role", "assistant", "content", a));
        });
    }

    private Map<String, Object> assistantToolCallMessage(List<ToolCallResult.ToolCall> toolCalls) {
        List<Map<String, Object>> calls = toolCalls.stream().map(tc -> Map.<String, Object>of(
                "id", tc.id(),
                "type", "function",
                "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())
        )).toList();

        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", (Object) null);
        msg.put("tool_calls", calls);
        return msg;
    }

    private void extractSources(String toolName, String result, List<CopilotAnswer.Source> sources) {
        if (!result.contains("ARQUIVO:")) return;
        for (String line : result.split("\n")) {
            if (line.startsWith("ARQUIVO:")) {
                String[] parts = line.replace("ARQUIVO:", "").trim().split("\\(score:");
                String path = parts[0].trim();
                double score = 1.0;
                if (parts.length > 1) {
                    try {
                        String clean = parts[1].replace(")", "").replace(',', '.').trim();
                        score = Double.parseDouble(clean);
                    } catch (Exception ignored) {
                        score = 1.0;
                    }
                }
                double finalScore = score;
                if (sources.stream().noneMatch(s -> s.path().equals(path))) {
                    sources.add(new CopilotAnswer.Source(path, finalScore));
                }
            }
        }
    }

    private String extractPatch(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = Pattern.compile("```(?:diff|patch)\\s*\\n([\\s\\S]*?)```").matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractPlan(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = Pattern.compile("(?i)#{1,3}\\s*Plano(?:\\s+de\\s+Ação)?[:\\s]*\\n([\\s\\S]*?)(?:\\n#{1,3}\\s+|$)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
