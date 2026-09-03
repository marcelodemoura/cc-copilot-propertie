package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.history.infra.CopilotInteractionRepository;
import br.com.mv.cccopilotpropertie.llm.application.LlmClient;
import br.com.mv.cccopilotpropertie.llm.application.ToolCallResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentLoop {

    private static final int MAX_ITERATIONS = 8;

    private static final String SYSTEM_PROMPT = """
            Você é um copiloto técnico especializado em análise de projetos de software Java/Kotlin.
            Você tem acesso a ferramentas para buscar código, ler arquivos na íntegra, navegar em diretórios, analisar DTOs, detectar breaking changes e auditar contratos HTTP.
            
            Regras:
            - Use as ferramentas disponíveis para coletar informações antes de responder.
            - Quando encontrar um arquivo relevante via busca, use 'read_file' se precisar de mais contexto ao redor do código.
            - Nunca invente informações — baseie-se apenas no que as ferramentas retornarem.
            - Se não encontrar informação suficiente, diga claramente.
            - Seja direto e técnico nas respostas.
            - Quando identificar riscos ou breaking changes, seja explícito sobre o impacto.
            - Se o usuário pedir refatoração, correção ou alteração de código, forneça um bloco ```diff ... ``` ou ```patch ... ``` com a mudança sugerida.
            - Se a resposta envolver múltiplos passos, inclua uma seção '### Plano de Ação:' detalhando a sequência recomendada.
            """;

    private final LlmClient llm;
    private final AgentTools tools;
    private final AgentToolExecutor executor;
    private final CopilotInteractionRepository historyRepo;

    public AgentLoop(LlmClient llm, AgentTools tools, AgentToolExecutor executor,
                     CopilotInteractionRepository historyRepo) {
        this.llm = llm;
        this.tools = tools;
        this.executor = executor;
        this.historyRepo = historyRepo;
    }

    public CopilotAnswer run(String tenantId, String kb, String question, String sessionId) {
        return run(tenantId, kb, question, sessionId, null);
    }

    public CopilotAnswer run(String tenantId, String kb, String question, String sessionId, AgentProgressListener listener) {

        if (listener != null) {
            listener.onProgress("status", "Analisando pergunta e contexto recente...");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        // injeta histórico recente da sessão para manter contexto
        loadHistory(tenantId, kb, sessionId, messages);

        messages.add(Map.of("role", "user", "content", question));

        List<CopilotAnswer.Source> sources = new ArrayList<>();

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

                String toolResult = executor.execute(tc.name(), tc.argumentsJson(), tenantId, kb);

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
            messages.add(Map.of("role", "user", "content", h.getQuestion()));
            messages.add(Map.of("role", "assistant", "content", h.getAnswer() != null ? h.getAnswer() : ""));
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
                double score = parts.length > 1
                        ? Double.parseDouble(parts[1].replace(")", "").trim())
                        : 1.0;
                if (sources.stream().noneMatch(s -> s.path().equals(path))) {
                    sources.add(new CopilotAnswer.Source(path, score));
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
