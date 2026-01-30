package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.alert.*;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
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
    private final AlertService alertService;
    private final CiEnforcer ciEnforcer;
    private final AuditService auditService;
    private final BreakingChangeAnalyzer breakingAnalyzer;

    private ConversationContext context;

    private static final Pattern DTO_PATTERN =
            Pattern.compile("(\\w+)DTO", Pattern.CASE_INSENSITIVE);

    public RagService(
            SearchService search,
            SearchRepository searchRepository,
            PromptAssembler promptAssembler,
            AnswerService answer,
            AlertService alertService,
            CiEnforcer ciEnforcer,
            AuditService auditService,
            BreakingChangeAnalyzer breakingAnalyzer
    ) {
        this.search = search;
        this.searchRepository = searchRepository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
        this.alertService = alertService;
        this.ciEnforcer = ciEnforcer;
        this.auditService = auditService;
        this.breakingAnalyzer = breakingAnalyzer;
    }

    // =========================================================
    // 🚀 ENTRYPOINT
    // =========================================================
    public CopilotAnswer ask(String tenantId, String kb, String question) {

        // 1️⃣ ENTENDIMENTO DO PROJETO
        if (isProjectQuestion(question)) {
            return projectOverview(tenantId, kb, question);
        }

        // 2️⃣ REMOÇÃO DE CAMPO (cria contexto)
        if (isRemoveFieldQuestion(question)) {
            return removeField(tenantId, kb, question);
        }

        // 3️⃣ BREAKING CHANGE
        if (isBreakingQuestion(question)) {
            return analyzeBreaking(tenantId, kb);
        }

        // 4️⃣ IMPACTO EM API / ENDPOINT
        if (isApiImpactQuestion(question)) {
            return analyzeApiImpact(tenantId, kb);
        }

        // 5️⃣ IMPACTO EXTERNO
        if (isExternalImpactQuestion(question)) {
            return analyzeExternalImpact(tenantId, kb);
        }

        // 6️⃣ USO / LOCALIZAÇÃO DE CAMPO
        if (isFieldUsageQuestion(question)) {
            return fieldUsage(tenantId, kb, question);
        }

        if (isFieldLocationQuestion(question)) {
            return fieldLocation(tenantId, kb, question);
        }

        // 7️⃣ ENDPOINTS GENÉRICOS
        if (isEndpointQuestion(question)) {
            return listEndpoints(tenantId, kb, question);
        }

        // 8️⃣ AUDITORIA DTO
        if (isAuditQuestion(question)) {
            return auditDto(tenantId, kb, question);
        }

        // 9️⃣ FALLBACK RAG
        return genericAnswer(tenantId, kb, question);
    }

    // =========================================================
    // 1️⃣ PROJETO
    // =========================================================
    private CopilotAnswer projectOverview(String tenantId, String kb, String q) {
        List<SearchResult> docs = search.search(tenantId, kb, "DTO", 20);
        if (docs.isEmpty()) {
            return simple("Código insuficiente.", 0);
        }

        return new CopilotAnswer(
                answer.ask(promptAssembler.build(q, docs)),
                toSources(docs),
                confidence(docs),
                null,
                null
        );
    }

    // =========================================================
    // 2️⃣ REMOÇÃO DE CAMPO
    // =========================================================
    private CopilotAnswer removeField(String tenantId, String kb, String q) {
        String field = extractField(q);
        List<SearchResult> docs = search.search(tenantId, kb, field, 20);

        String dto = inferDto(docs);

        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD,
                ChangeType.REMOVE,
                field,
                dto,
                null,
                null
        );

        context = new ConversationContext(dto, field, change);

        if (docs.isEmpty()) {
            return simple("Campo `" + field + "` não é usado. Remoção segura.", 1.0);
        }

        return new CopilotAnswer(
                "Campo `" + field + "` possui usos e pode gerar impacto.",
                toSources(docs),
                confidence(docs),
                null,
                null
        );
    }

    // =========================================================
    // 3️⃣ BREAKING
    // =========================================================
    private CopilotAnswer analyzeBreaking(String tenantId, String kb) {
        if (context == null || context.change == null) {
            return simple("Nenhuma mudança em contexto.", 1.0);
        }

        ImpactAnalysis impact =
                ImpactAnalysis.from(tenantId, kb, context.change, searchRepository);

        BreakingAnalysisResult result =
                breakingAnalyzer.analyze(context.change, impact);

        return new CopilotAnswer(
                """
                Análise de Breaking Change:
                
                • Elemento: %s
                • Tipo: %s
                • Classificação: %s
                • Motivo: %s
                • Versionar: %s
                """.formatted(
                        context.change.elementName(),
                        context.change.type(),
                        result.breakingType(),
                        result.reason(),
                        result.requiresVersioning() ? "SIM" : "NÃO"
                ),
                List.of(),
                1.0,
                null,
                null
        );
    }

    // =========================================================
    // 4️⃣ API / ENDPOINT
    // =========================================================
    private CopilotAnswer analyzeApiImpact(String tenantId, String kb) {
        if (context == null || context.dto == null) {
            return simple("Nenhum DTO em contexto.", 1.0);
        }

        List<SearchResult> endpoints =
                searchRepository.findEndpointsUsingDto(tenantId, kb, context.dto);

        if (endpoints.isEmpty()) {
            return simple("DTO `" + context.dto + "` não impacta APIs.", 1.0);
        }

        return new CopilotAnswer(
                "DTO `" + context.dto + "` impacta endpoints:",
                toSources(endpoints),
                1.0,
                null,
                null
        );
    }

    // =========================================================
    // 5️⃣ IMPACTO EXTERNO
    // =========================================================
    private CopilotAnswer analyzeExternalImpact(String tenantId, String kb) {
        if (context == null || context.dto == null) {
            return simple("Nenhum DTO em contexto.", 1.0);
        }

        List<SearchResult> external =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId, kb, context.dto);

        return simple(
                external.isEmpty()
                        ? "DTO `" + context.dto + "` não possui uso externo."
                        : "DTO `" + context.dto + "` é contrato externo.",
                1.0
        );
    }

    // =========================================================
    // 6️⃣ CAMPO
    // =========================================================
    private CopilotAnswer fieldUsage(String tenantId, String kb, String q) {
        String field = extractField(q);
        List<SearchResult> docs = search.search(tenantId, kb, field, 20);
        return docs.isEmpty()
                ? simple("Campo não utilizado.", 1.0)
                : new CopilotAnswer("Usos do campo:", toSources(docs), confidence(docs), null, null);
    }

    private CopilotAnswer fieldLocation(String tenantId, String kb, String q) {
        return fieldUsage(tenantId, kb, q);
    }

    // =========================================================
    // 7️⃣ ENDPOINTS
    // =========================================================
    private CopilotAnswer listEndpoints(String tenantId, String kb, String q) {
        List<SearchResult> docs = search.search(tenantId, kb, "@RestController", 20);
        return docs.isEmpty()
                ? simple("Nenhum endpoint encontrado.", 1.0)
                : new CopilotAnswer("Endpoints:", toSources(docs), confidence(docs), null, null);
    }

    // =========================================================
    // 8️⃣ AUDITORIA
    // =========================================================
    private CopilotAnswer auditDto(String tenantId, String kb, String q) {
        String dto = extractDto(q);
        context = new ConversationContext(dto, null, null);

        List<SearchResult> usages =
                searchRepository.findUsagesByClassName(tenantId, kb, dto);

        return simple("DTO `" + dto + "` possui " + usages.size() + " usos.", 1.0);
    }

    // =========================================================
    // 9️⃣ FALLBACK
    // =========================================================
    private CopilotAnswer genericAnswer(String tenantId, String kb, String q) {
        List<SearchResult> docs = search.search(tenantId, kb, q, 12);
        return docs.isEmpty()
                ? simple("Nada encontrado.", 0)
                : new CopilotAnswer(
                answer.ask(promptAssembler.build(q, docs)),
                toSources(docs),
                confidence(docs),
                null,
                null
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private String extractField(String q) {
        Matcher m = Pattern.compile("campo\\s+(\\w+)", Pattern.CASE_INSENSITIVE).matcher(q);
        return m.find() ? m.group(1) : q;
    }

    private String extractDto(String q) {
        Matcher m = DTO_PATTERN.matcher(q);
        return m.find() ? m.group(1) + "DTO" : q;
    }

    private String inferDto(List<SearchResult> docs) {
        return docs.stream()
                .map(SearchResult::path)
                .filter(p -> p.endsWith("DTO.java"))
                .map(p -> p.substring(p.lastIndexOf("/") + 1).replace(".java", ""))
                .findFirst()
                .orElse(null);
    }

    private boolean isProjectQuestion(String q) {
        return q.toLowerCase().contains("o que esse projeto faz");
    }

    private boolean isRemoveFieldQuestion(String q) {
        return q.toLowerCase().contains("remover") && q.toLowerCase().contains("campo");
    }

    private boolean isBreakingQuestion(String q) {
        return q.toLowerCase().contains("breaking") || q.toLowerCase().contains("quebra");
    }

    private boolean isApiImpactQuestion(String q) {
        return q.toLowerCase().contains("api") || q.toLowerCase().contains("endpoint");
    }

    private boolean isExternalImpactQuestion(String q) {
        return q.toLowerCase().contains("outro sistema") || q.toLowerCase().contains("externo");
    }

    private boolean isFieldUsageQuestion(String q) {
        return q.toLowerCase().contains("usado");
    }

    private boolean isFieldLocationQuestion(String q) {
        return q.toLowerCase().contains("onde") && q.toLowerCase().contains("campo");
    }

    private boolean isEndpointQuestion(String q) {
        return q.toLowerCase().contains("endpoint");
    }

    private boolean isAuditQuestion(String q) {
        return q.toLowerCase().contains("auditoria");
    }

    private double confidence(List<SearchResult> docs) {
        return docs.stream().mapToDouble(SearchResult::score).average().orElse(0);
    }

    private List<CopilotAnswer.Source> toSources(List<SearchResult> docs) {
        return docs.stream()
                .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                .toList();
    }

    private CopilotAnswer simple(String msg, double c) {
        return new CopilotAnswer(msg, List.of(), c, null, null);
    }

    private record ConversationContext(
            String dto,
            String field,
            ChangeSet change
    ) {}
}
