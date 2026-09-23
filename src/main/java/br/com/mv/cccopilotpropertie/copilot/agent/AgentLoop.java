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
            Você é um copiloto técnico especializado em análise, arquitetura, refatoração e correção de projetos de software Java/Kotlin.
            Você tem acesso a ferramentas para buscar código, ler arquivos na íntegra, navegar na árvore de arquivos, analisar DTOs, detectar breaking changes, auditar contratos HTTP e aplicar alterações/criações de código ('modify_file', 'write_file').
            
            Diretrizes de Resposta (Objetividade e Clareza):
            - Seja ESTRITAMENTE CONCISO, DIRETO E OBJETIVO. Vá direto ao ponto sem saudações, introduções (ex: 'Com base na análise...', 'Certamente...') ou conclusões óbvias.
            - Não seja repetitivo: responda exclusivamente ao que foi perguntado, evitando gerar tópicos ou seções desnecessárias.
            - Ao sugerir código, forneça apenas os blocos relevantes (diff ou patch pontual), nunca reproduza arquivos inteiros.
            - Quando o usuário perguntar o que o projeto faz ou sua arquitetura:
              1. Comece lendo o arquivo de build (build.gradle ou pom.xml) ou o README.md com 'read_file'.
              2. Use 'search_code' para buscar a classe principal com @SpringBootApplication e os controllers (@RestController).
              3. O 'list_files' já retorna a árvore hierárquica compactada — não navegue pasta a pasta repetidamente.
              4. Resuma sucintamente em poucos pontos: Propósito, Stack e Principais Módulos.
            - Use as ferramentas disponíveis para coletar informações antes de responder e nunca invente dados.
            - Se não encontrar informação suficiente, informe objetivamente o que encontrou e o que faltou validar.
            - Ao identificar riscos ou breaking changes, aponte diretamente o impacto de forma resumida.
            - Se o usuário pedir para CORRIGIR, REFATORAR ou APLICAR alterações:
              1. Leia o arquivo com 'read_file' para capturar o trecho exato.
              2. Execute 'modify_file' com o caminho, 'targetContent' exato, 'replacementContent' e justificativa concisa.
              3. Para criar novos arquivos, use 'write_file'.
              4. Confirme a alteração e exiba apenas o diff na resposta final.
            - Se a resposta exigir múltiplos passos, apresente um '### Plano de Ação:' direto em tópicos curtos.
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

        String effectiveSystemPrompt = SYSTEM_PROMPT;
        if (projectRepo != null && kb != null && !kb.isBlank()) {
            try {
                UUID id = UUID.fromString(kb);
                var pOpt = projectRepo.findById(id);
                if (pOpt.isPresent()) {
                    var p = pOpt.get();
                    effectiveSystemPrompt += """

                            Informações do Projeto Ativo:
                            - Nome do Projeto: %s
                            - Caminho Raiz no Sistema: %s
                            """.formatted(p.getName(), p.getRootPath());
                }
            } catch (Exception ignored) {
            }
        }

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
                "content", "Com base nas informações coletadas nas etapas anteriores, elabore sua resposta final de forma direta, concisa e objetiva para o usuário, focando na solução e sem repetições."
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
