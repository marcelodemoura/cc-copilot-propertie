package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagService {

    private final SearchService search;
    private final SearchRepository searchRepository;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;

    private static final Pattern EXTENDS_PATTERN =
            Pattern.compile("extends\\s+(\\w+)");
    private static final Pattern DTO_PATTERN =
            Pattern.compile("(\\w+DTO)");

    private ConversationContext lastContext;

    private record ConversationContext(
            String lastDto,
            String lastKnowledgeBase
    ) {
    }

    public RagService(
            SearchService search,
            SearchRepository searchRepository,
            PromptAssembler promptAssembler,
            AnswerService answer
    ) {
        this.search = search;
        this.searchRepository = searchRepository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
    }

    // =========================================================
    // 🚀 ENTRYPOINT
    // =========================================================
    public CopilotAnswer ask(
            String tenantId,
            String knowledgeBase,
            String question
    ) {
        double confidence;

        // ===============================
        // 🧠 PERGUNTAS CANÔNICAS
        // ===============================
        if (isCanonicalQuestion(question)) {

            if (lastContext == null) {
                return new CopilotAnswer(
                        "Não há contexto anterior para avaliar.",
                        List.of(),
                        0.0
                );
            }

            String resolvedQuestion =
                    "Existe risco no uso do " + lastContext.lastDto() + "?";

            return ask(
                    tenantId,
                    lastContext.lastKnowledgeBase(),
                    resolvedQuestion
            );
        }

        List<SearchResult> docs =
                search.search(tenantId, knowledgeBase, question, 12);

        if (docs.isEmpty()) {
            return new CopilotAnswer(
                    "Não encontrei informações suficientes na base de conhecimento.",
                    List.of(),
                    0.0
            );
        }

        confidence = calculateConfidence(docs);

        // ===============================
        // 🔎 IDENTIFICA DTO E GUARDA CONTEXTO
        // ===============================
        Optional<String> dtoOpt = extractDtoName(question);
        dtoOpt.ifPresent(d ->
                lastContext = new ConversationContext(d, knowledgeBase)
        );

        // ===============================
        // 🧬 HERANÇA CONSCIENTE
        // ===============================
        List<SearchResult> enrichedDocs =
                enrichWithInheritance(tenantId, knowledgeBase, docs);

        // =====================================================
        // 🚨 PASSO 1 + 2 — AUDITORIA + RISCO
        // =====================================================
        if (isAuditQuestion(question)) {

            if (dtoOpt.isEmpty()) {
                return new CopilotAnswer(
                        "Não foi possível identificar o DTO a ser auditado.",
                        List.of(),
                        confidence
                );
            }

            String dto = dtoOpt.get();

            // 🌍 DTO existe globalmente?
            Optional<SearchResult> globalDto =
                    searchRepository.findDtoDefinitionGlobal(tenantId, dto);

            return globalDto.map(searchResult -> auditDtoUsage(
                    tenantId,
                    knowledgeBase,
                    dto,
                    searchResult,
                    confidence
            )).orElseGet(() -> new CopilotAnswer(
                    "O DTO " + dto + " não foi encontrado em nenhum projeto indexado.",
                    List.of(),
                    0.0
            ));

        }

        // =====================================================
        // 🔗 MODO NORMAL
        // =====================================================
        UsageContext usageContext =
                enrichWithUsages(tenantId, knowledgeBase, question, enrichedDocs);

        String response = answer.ask(usageContext.prompt());

        List<CopilotAnswer.Source> sources =
                usageContext.usages().isEmpty()
                        ? enrichedDocs.stream()
                        .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                        .toList()
                        : usageContext.usages().stream()
                        .map(u -> new CopilotAnswer.Source(u.path(), u.score()))
                        .toList();

        return new CopilotAnswer(response, sources, confidence);
    }

    // =========================================================
    // 🧬 HERANÇA
    // =========================================================
    private List<SearchResult> enrichWithInheritance(
            String tenantId,
            String knowledgeBase,
            List<SearchResult> docs
    ) {
        SearchResult child = docs.get(0);

        Optional<String> parent =
                extractParentClass(child.content());

        if (parent.isEmpty()) return docs;

        Optional<SearchResult> parentDoc =
                searchRepository.findByClassName(
                        tenantId,
                        knowledgeBase,
                        parent.get()
                );

        if (parentDoc.isEmpty()) return docs;

        List<SearchResult> enriched = new ArrayList<>();
        enriched.add(parentDoc.get());
        enriched.addAll(docs);
        return enriched;
    }

    private Optional<String> extractParentClass(String code) {
        Matcher m = EXTENDS_PATTERN.matcher(code);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    // =========================================================
    // 🔗 USO NORMAL
    // =========================================================
    private UsageContext enrichWithUsages(
            String tenantId,
            String knowledgeBase,
            String question,
            List<SearchResult> docs
    ) {
        Optional<String> dtoOpt = extractDtoName(question);

        if (dtoOpt.isEmpty()) {
            return new UsageContext(
                    promptAssembler.build(question, docs),
                    List.of()
            );
        }

        String dto = dtoOpt.get();

        List<SearchResult> usages =
                searchRepository.findUsagesByClassName(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        StringBuilder ctx = new StringBuilder();
        ctx.append("USO DO DTO ").append(dto).append(":\n");

        usages.forEach(u ->
                ctx.append("- ")
                        .append(classifyUsage(u.path()))
                        .append(": ")
                        .append(u.path())
                        .append("\n")
        );

        ctx.append("\nCONTEÚDO RELEVANTE:\n");

        return new UsageContext(
                ctx + promptAssembler.build(question, docs),
                usages
        );
    }

    // =========================================================
    // 🚨 AUDITORIA + SCORE DE RISCO
    // =========================================================
    private CopilotAnswer auditDtoUsage(
            String tenantId,
            String knowledgeBase,
            String dto,
            SearchResult globalDto,
            double confidence
    ) {
        String dtoContent = globalDto.content();

        List<SearchResult> usages =
                searchRepository.findUsagesByClassName(
                        tenantId,
                        knowledgeBase,
                        dto
                );
        // =========================
        // 🌍 Uso inter-projetos
        // =========================
        List<SearchResult> externalUsages =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId,
                        knowledgeBase,
                        dto
                );


        StringBuilder audit = new StringBuilder();
        audit.append("Auditoria do uso do ").append(dto).append(":\n\n");

        // 1️⃣ Campos obrigatórios
        boolean hasRequiredFields =
                dtoContent.contains("@NotNull") || dtoContent.contains("@NotBlank");

        audit.append("Campos obrigatórios:\n");
        audit.append(hasRequiredFields
                ? "- Possui campos obrigatórios definidos\n"
                : "- Não possui campos obrigatórios definidos\n");

        // 2️⃣ Análise de uso
        audit.append("\nAnálise de uso:\n");

        if (usages.isEmpty()) {
            audit.append("⚠️ Nenhum uso explícito encontrado.\n");
        } else {
            usages.forEach(u ->
                    audit.append("- ")
                            .append(classifyUsage(u.path()))
                            .append(": ")
                            .append(u.path())
                            .append("\n")
                            .append("  ⚠️ Não há evidência clara de validação.\n")
            );
        }

        // 3️⃣ Score de risco
        int usageCount = usages.size();
        boolean hasExplicitValidation = false;

        boolean usedInOtherProjects = !externalUsages.isEmpty();

        String risk;
        if (hasRequiredFields && !hasExplicitValidation && usedInOtherProjects) {
            risk = "ALTO";
        } else if (hasRequiredFields && usageCount > 0) {
            risk = "MÉDIO";
        } else {
            risk = "BAIXO";
        }

        audit.append("\nRisco identificado:\n");
        audit.append("- Nível de risco: ").append(risk).append("\n");
        audit.append("Motivos:\n");

        if (usedInOtherProjects) {
            audit.append("• DTO utilizado em mais de um projeto (risco inter-projetos)\n");
        }

        if (hasRequiredFields) {
            audit.append("• DTO possui campos obrigatórios\n");
        }

        if (!hasExplicitValidation) {
            audit.append("• Não há validação explícita identificada\n");
        }

        audit.append("• Quantidade de usos encontrados: ")
                .append(usageCount).append("\n");

// =========================
// ✅ RECOMENDAÇÕES
// =========================
        audit.append("\nRecomendações:\n");

        if (risk.equals("ALTO")) {
            audit.append("• Criar DTO específico para cada projeto\n");
            audit.append("• Garantir validação com @Valid no Controller\n");
            audit.append("• Evitar uso direto do DTO em mensageria\n");
            audit.append("• Considerar contrato compartilhado\n");
        } else if (risk.equals("MÉDIO")) {
            audit.append("• Revisar validação nos pontos de entrada\n");
            audit.append("• Criar testes unitários para o DTO\n");
        } else {
            audit.append("• Nenhuma ação imediata necessária\n");
        }

        return new CopilotAnswer(
                audit.toString(),
                usages.stream()
                        .map(u -> new CopilotAnswer.Source(u.path(), u.score()))
                        .toList(),
                confidence
        );
    }


    // =========================================================
    // 🔍 HELPERS
    // =========================================================
    private Optional<String> extractDtoName(String question) {
        Matcher m = DTO_PATTERN.matcher(question);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private boolean isAuditQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("risco")
                || q.contains("respeita")
                || q.contains("validado")
                || q.contains("auditoria");
    }

    private boolean isCanonicalQuestion(String q) {
        q = q.toLowerCase().trim();
        return q.equals("isso está correto?")
                || q.equals("está correto?")
                || q.equals("isso está certo?")
                || q.equals("tem risco?")
                || q.equals("isso tem risco?")
                || q.equals("existe risco?");
    }

    private String classifyUsage(String path) {
        String p = path.toLowerCase();
        if (p.contains("controller")) return "Controller";
        if (p.contains("service")) return "Service";
        if (p.contains("queue") || p.contains("producer")) return "Mensageria";
        if (p.contains("/dto/")) return "DTO dependente";
        return "Outro";
    }

    private double calculateConfidence(List<SearchResult> docs) {
        return docs.stream()
                .limit(3)
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0.0);
    }

    // =========================================================
    // 🧱 SUPPORT
    // =========================================================
    private record UsageContext(
            String prompt,
            List<SearchResult> usages
    ) {
    }
}
