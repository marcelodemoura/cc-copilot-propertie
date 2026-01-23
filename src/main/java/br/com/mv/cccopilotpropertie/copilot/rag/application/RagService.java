package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.*;
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
            String dto,
            String knowledgeBase
    ) {}

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

        // ===============================
        // 🧠 PERGUNTAS CANÔNICAS
        // ===============================
        if (isCanonicalQuestion(question)) {

            if (lastContext == null) {
                return new CopilotAnswer(
                        "Não há contexto anterior para avaliar.",
                        List.of(),
                        0.0,
                        null
                );
            }

            return ask(
                    tenantId,
                    lastContext.knowledgeBase(),
                    "Existe risco no uso do " + lastContext.dto() + "?"
            );
        }

        List<SearchResult> docs =
                search.search(tenantId, knowledgeBase, question, 12);

        if (docs.isEmpty()) {
            return new CopilotAnswer(
                    "Não encontrei informações suficientes na base de conhecimento.",
                    List.of(),
                    0.0,
                    null
            );
        }

        double confidence = calculateConfidence(docs);

        Optional<String> dtoOpt = extractDtoName(question);
        dtoOpt.ifPresent(d ->
                lastContext = new ConversationContext(d, knowledgeBase)
        );

        List<SearchResult> enrichedDocs =
                enrichWithInheritance(tenantId, knowledgeBase, docs);

        // =====================================================
        // 🚨 AUDITORIA
        // =====================================================
        if (isAuditQuestion(question)) {

            if (dtoOpt.isEmpty()) {
                return new CopilotAnswer(
                        "Não foi possível identificar o DTO a ser auditado.",
                        List.of(),
                        confidence,
                        null
                );
            }

            String dto = dtoOpt.get();

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
                    0.0,
                    null
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

        return new CopilotAnswer(
                response,
                sources,
                confidence,
                null
        );
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

        Optional<String> parent = extractParentClass(child.content());
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
    // 🚨 AUDITORIA + SCORE
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

        List<SearchResult> externalUsages =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        boolean hasRequiredFields =
                dtoContent.contains("@NotNull") || dtoContent.contains("@NotBlank");

        boolean usedInOtherProjects = !externalUsages.isEmpty();
        boolean hasExplicitValidation = false;

        int usageCount = usages.size();

        String risk =
                hasRequiredFields && usedInOtherProjects && !hasExplicitValidation
                        ? "ALTO"
                        : hasRequiredFields && usageCount > 0
                        ? "MÉDIO"
                        : "BAIXO";

        String audit = "Auditoria do uso do " + dto + ":\n\n" +
                "Risco identificado: " + risk + "\n";

        List<String> recommendations = getRecommendations(risk);

        DtoAuditResult structured = new DtoAuditResult(
                dto,
                risk,
                usedInOtherProjects,
                hasRequiredFields,
                hasExplicitValidation,
                usageCount,
                recommendations
        );

        List<CopilotAnswer.Source> sources = new ArrayList<>();
        usages.forEach(u -> sources.add(new CopilotAnswer.Source(u.path(), u.score())));
        externalUsages.forEach(u -> sources.add(new CopilotAnswer.Source(u.path(), u.score())));

        return new CopilotAnswer(
                audit,
                sources,
                confidence,
                structured
        );
    }

    private List<String> getRecommendations(String risk) {
        return switch (risk) {
            case "ALTO" -> List.of(
                    "Criar DTO específico por projeto",
                    "Garantir validação com @Valid",
                    "Evitar uso direto em mensageria"
            );
            case "MÉDIO" -> List.of(
                    "Revisar validações",
                    "Criar testes unitários"
            );
            default -> List.of("Nenhuma ação imediata necessária");
        };
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
                || q.equals("tem risco?")
                || q.equals("existe risco?");
    }

    private String classifyUsage(String path) {
        String p = path.toLowerCase();
        if (p.contains("controller")) return "Controller";
        if (p.contains("service")) return "Service";
        if (p.contains("queue")) return "Mensageria";
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

    private record UsageContext(
            String prompt,
            List<SearchResult> usages
    ) {}
}
