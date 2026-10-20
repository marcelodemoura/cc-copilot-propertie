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

    private static final int MAX_ITERATIONS = 15;

    private static final String SYSTEM_PROMPT = """
            Você é um engenheiro de software e copiloto autônomo poliglota (Java, Kotlin, TypeScript/JavaScript, HTML/CSS/Tailwind, Node.js, React, Angular, Vue, Python, Go, C#, Docker/Infra) e especialista em bancos de dados (Oracle SQL/PL-SQL, PostgreSQL, SQL Server/T-SQL, MySQL, MongoDB, Redis).
            Você possui ferramentas para listar projetos ('list_projects'), inspecionar alterações Git ('git_changes'), buscar código ('search_code'), ler/listar arquivos ('read_file', 'list_files'), auditar contratos/DTOs/schemas e editar/criar arquivos ('modify_file', 'write_file').

            REGRA #1 — ISOLAMENTO ESTRITO DO PROJETO ATIVO:
            - Opere EXCLUSIVAMENTE no Projeto Ativo informado abaixo por padrão (deixe o parâmetro 'project' vazio nas ferramentas).
            - É ESTRITAMENTE PROIBIDO buscar, listar, ler ou editar arquivos de outro projeto do workspace (como 'command-center-view') quando o usuário disser "neste projeto", "dele", "o front dele", "a interface desse projeto" ou pedir foco no projeto atual, mesmo que o histórico recente da conversa mencione outro projeto. Só acesse outro projeto se o usuário pedir explicitamente pelo nome desse outro projeto na mensagem atual.
            - Projetos Java/Spring Boot frequentemente possuem sua própria interface gráfica / SPA em `src/main/resources/static/index.html` (ou `src/main/resources/templates/`). NUNCA diga que "não há front-end neste projeto" sem verificar `src/main/resources/static/` e os arquivos de UI listados no contexto do Projeto Ativo!

            REGRA #2 — GOVERNANÇA ENTRE ANÁLISE (SOMENTE LEITURA) E IMPLEMENTAÇÃO (ESCRITA):
            - MODO ANÁLISE / SUGESTÃO (SOMENTE LEITURA): Se o usuário pedir apenas para analisar, explicar, diagnosticar, verificar ou sugerir opções/melhorias (ex: "dá uma analisada", "sugira 3 opções de melhoria", "como é mostrada a interface", "o que faz"), NUNCA chame `modify_file` nem `write_file`. Use apenas ferramentas de leitura (`list_files`, `read_file`, `search_code`, `git_changes`).
            - MODO IMPLEMENTAÇÃO / ALTERAÇÃO (ESCRITA REAL): Quando o usuário pedir, ordenar ou autorizar que você faça, aplique, implemente ou altere algo (ex: "faça", "aplique", "implemente", "melhore o front", "altere", "modifique", "aplique a opção X", "atualize o arquivo"), você DEVE executar `read_file` e aplicar a mudança real no arquivo usando `modify_file` (ou `write_file` para arquivos novos) antes de responder! É PROIBIDO apenas mostrar snippets no chat ou perguntar "Qual opção prefere que eu aplique?" quando o usuário já pediu para executar a melhoria.

            REGRA #3 — LEITURA COMPLETA E EDIÇÃO CIRÚRGICA DE ARQUIVOS:
            - Ao analisar ou editar um arquivo grande (como `src/main/resources/static/index.html`), não se limite às primeiras 250 linhas! Consulte o `[Mapa das próximas seções]` retornado pelo `read_file` e chame `read_file` passando `startLine` e `endLine` para inspecionar o HTML, CSS e as funções JavaScript relevantes.
            - Para alterar arquivos existentes, use SEMPRE `modify_file` passando em `targetContent` o trecho exato lido do arquivo (sem os prefixos de número de linha `123: `) e em `replacementContent` o código novo completo. NUNCA use `write_file` para sobrescrever um arquivo grande existente com código parcial.
            - Na iteração 1, NUNCA devolva perguntas ou menus de opções antes de chamar as ferramentas necessárias.

            REGRAS ESTRITAS DE FORMATAÇÃO E BREVIDADE:
            - RESPOSTAS CURTAS E CIRÚRGICAS: Responda de forma objetiva e estruturada, sem saudações, sem introduções ("Com base na análise...") e sem conclusões genéricas ("Em resumo...").
            - Ao editar ou criar arquivos ('modify_file', 'write_file'), confirme quais arquivos foram alterados no disco, resuma em tópicos curtos o que foi implementado e mostre o bloco ```diff essencial.
            - Banco de Dados e Queries:
              * Identifique as tabelas/colunas reais lendo as entidades (@Entity/@Table, migrations Flyway/Liquibase, schemas SQL ou packages PL/SQL) antes de escrever queries.
            - Trabalho Entre Projetos (Cross-Project):
              * Apenas quando a tarefa pedir explicitamente integração entre múltiplos projetos, passe o nome ou ID no parâmetro 'project'.
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

                String toolResult;
                if (consecutiveSameCall >= 3) {
                    toolResult = "[Bloqueio de Loop: Você já executou '" + tc.name() + "' com estes mesmos argumentos. " +
                            "NÃO repita a mesma chamada. Se estiver lendo um arquivo grande com 'read_file', informe 'startLine' e 'endLine' para ler o próximo trecho, " +
                            "ou execute 'modify_file' / elabore sua resposta final.]";
                } else {
                    toolResult = executor.execute(tc.name(), tc.argumentsJson(), tenantId, kb);
                }

                if ("list_files".equals(tc.name())) {
                    consecutiveListFiles++;
                    if (consecutiveListFiles >= 2) {
                        toolResult += "\n[Dica de síntese: A estrutura de pastas principal já foi mapeada. Use 'read_file' ou 'search_code' nos arquivos desejados ou elabore sua resposta com as informações já disponíveis.]";
                    }
                } else {
                    consecutiveListFiles = 0;
                }

                if (consecutiveSameCall == 2) {
                    toolResult += "\n[Aviso do Sistema: Chamada repetida detectada com os mesmos parâmetros. Evite repetir esta ação; avance para o próximo passo (ex: ler próximas linhas com startLine/endLine, aplicar modify_file ou responder).]";
                }

                // Limita payload excessivo de ferramentas para não estourar a janela de contexto do LLM
                if (toolResult != null && toolResult.length() > 24000) {
                    toolResult = toolResult.substring(0, 24000) + "\n... [trecho restante truncado para preservar a janela de contexto do LLM; use startLine/endLine no read_file para ler seções específicas]";
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
            java.nio.file.Path activeRoot = null;
            if (kb != null && !kb.isBlank()) {
                try {
                    UUID id = UUID.fromString(kb);
                    var pOpt = projectRepo.findById(id);
                    if (pOpt.isPresent()) {
                        var p = pOpt.get();
                        sb.append("\nPROJETO ATIVO ATUAL (FOCO OBRIGATÓRIO — padrão quando 'project' está vazio):\n")
                                .append("- Nome: ").append(p.getName())
                                .append(" | ID: ").append(p.getId())
                                .append(" | Raiz: ").append(p.getRootPath()).append("\n");
                        activeRoot = java.nio.file.Path.of(p.getRootPath()).toAbsolutePath().normalize();
                    }
                } catch (Exception ignored) {
                }
            }

            if (activeRoot != null && java.nio.file.Files.isDirectory(activeRoot)) {
                List<String> uiFiles = detectFrontendEntrypoints(activeRoot);
                if (!uiFiles.isEmpty()) {
                    sb.append("- Interface Gráfica / Frontend (UI) próprio deste projeto: ")
                            .append(String.join(", ", uiFiles))
                            .append(" (sempre inspecione/edite estes arquivos quando o usuário falar de 'front', 'UI', 'tela' ou 'interface' deste projeto!)\n");
                }
            }

            var allProjects = (tenantId != null && !tenantId.isBlank())
                    ? projectRepo.findByTenantId(tenantId)
                    : projectRepo.findAll();
            if (allProjects != null && !allProjects.isEmpty()) {
                sb.append("\nOutros Projetos no Workspace (NÃO acesse a menos que o usuário peça explicitamente pelo nome na pergunta atual):\n");
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

    private List<String> detectFrontendEntrypoints(java.nio.file.Path root) {
        List<String> candidates = List.of(
                "src/main/resources/static/index.html",
                "src/main/resources/templates/index.html",
                "public/index.html",
                "index.html",
                "src/App.vue",
                "src/App.tsx",
                "src/App.jsx",
                "src/main.ts",
                "src/main.js"
        );
        List<String> found = new ArrayList<>();
        for (String rel : candidates) {
            java.nio.file.Path p = root.resolve(rel);
            if (java.nio.file.Files.exists(p) && java.nio.file.Files.isRegularFile(p)) {
                found.add(rel);
            }
        }
        return found;
    }

    private void loadHistory(String tenantId, String kb, String sessionId, List<Map<String, Object>> messages) {
        if (sessionId == null || sessionId.isBlank()) return;

        var history = historyRepo
                .findTop6ByTenantIdAndKnowledgeBaseAndSessionIdOrderByCreatedAtDesc(tenantId, kb, sessionId);

        Collections.reverse(history);

        history.forEach(h -> {
            String q = h.getQuestion() != null ? h.getQuestion() : "";
            String a = h.getAnswer() != null ? h.getAnswer() : "";
            if (a.length() > 2500) {
                a = a.substring(0, 2500) + "\n... [resumo do contexto anterior]";
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
