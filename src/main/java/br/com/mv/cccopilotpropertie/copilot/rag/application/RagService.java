package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.alert.*;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;
import br.com.mv.cccopilotpropertie.copilot.intent.IntentClassifier;
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
    private final SearchRepository repository;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;
    private final AuditService auditService;
    private final BreakingChangeAnalyzer breakingAnalyzer;
    private final IntentClassifier intentClassifier;

    private ConversationContext context;

    private static final Pattern DTO_PATTERN =
            Pattern.compile("(\\w+)DTO", Pattern.CASE_INSENSITIVE);

    public RagService(
            SearchService search,
            SearchRepository repository,
            PromptAssembler promptAssembler,
            AnswerService answer,
            AuditService auditService,
            BreakingChangeAnalyzer breakingAnalyzer,
            IntentClassifier intentClassifier
    ) {
        this.search = search;
        this.repository = repository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
        this.auditService = auditService;
        this.breakingAnalyzer = breakingAnalyzer;
        this.intentClassifier = intentClassifier;
    }

    // =========================================================
    // 🚀 ENTRYPOINT
    // =========================================================
    public CopilotAnswer ask(String tenantId, String kb, String question) {

        CopilotIntent intent = intentClassifier.classify(question);

        return switch (intent) {

            case PROJECT_UNDERSTANDING ->
                    projectOverview(tenantId, kb, question);

            case FIELD_REMOVAL ->
                    removeField(tenantId, kb, question);

            case BREAKING_CHANGE ->
                    analyzeBreaking(tenantId, kb);

            case HTTP_CONTRACT_IMPACT ->
                    analyzeApiImpact(tenantId, kb);

            case EXTERNAL_IMPACT ->
                    analyzeExternalImpact(tenantId, kb);

            case FIELD_USAGE ->
                    fieldUsage(tenantId, kb, question);

            case ENDPOINT_USAGE ->
                    listEndpoints(tenantId, kb);

            case DTO_AUDIT ->
                    auditDto(tenantId, kb, question);

            case GENERIC_QUESTION ->
                    genericAnswer(tenantId, kb, question);
        };
    }

    // =========================================================
    // PROJETO
    // =========================================================
    private CopilotAnswer projectOverview(String tenantId, String kb, String q) {
        List<SearchResult> docs = search.search(tenantId, kb, "DTO", 20);

        if (docs.isEmpty()) {
            return simple("Código insuficiente para análise.", 0);
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
    // REMOÇÃO DE CAMPO
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
            return simple("Campo `" + field + "` não é utilizado. Remoção segura.", 1.0);
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
    // BREAKING CHANGE
    // =========================================================
    private CopilotAnswer analyzeBreaking(String tenantId, String kb) {

        if (context == null || context.change == null) {
            return simple("Nenhuma alteração em contexto.", 1.0);
        }

        ImpactAnalysis impact =
                ImpactAnalysis.from(tenantId, kb, context.change, repository);

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
    // API / ENDPOINT
    // =========================================================
    private CopilotAnswer analyzeApiImpact(String tenantId, String kb) {

        if (context == null || context.dto == null) {
            return simple("Nenhum DTO em contexto.", 1.0);
        }

        List<SearchResult> endpoints =
                repository.findEndpointsUsingDto(tenantId, kb, context.dto);

        if (endpoints.isEmpty()) {
            return simple("DTO `" + context.dto + "` não impacta APIs.", 1.0);
        }

        return new CopilotAnswer(
                "DTO `" + context.dto + "` impacta os seguintes endpoints:",
                toSources(endpoints),
                1.0,
                null,
                null
        );
    }

    // =========================================================
    // IMPACTO EXTERNO
    // =========================================================
    private CopilotAnswer analyzeExternalImpact(String tenantId, String kb) {

        if (context == null || context.dto == null) {
            return simple("Nenhum DTO em contexto.", 1.0);
        }

        List<SearchResult> external =
                repository.findUsagesInOtherKnowledgeBases(
                        tenantId, kb, context.dto
                );

        return simple(
                external.isEmpty()
                        ? "DTO `" + context.dto + "` não possui uso externo."
                        : "DTO `" + context.dto + "` é contrato externo.",
                1.0
        );
    }

    // =========================================================
    // CAMPO
    // =========================================================
    private CopilotAnswer fieldUsage(String tenantId, String kb, String q) {
        String field = extractField(q);
        List<SearchResult> docs = search.search(tenantId, kb, field, 20);

        return docs.isEmpty()
                ? simple("Campo não utilizado.", 1.0)
                : new CopilotAnswer(
                "Usos do campo:",
                toSources(docs),
                confidence(docs),
                null,
                null
        );
    }

    // =========================================================
    // ENDPOINTS
    // =========================================================
    private CopilotAnswer listEndpoints(String tenantId, String kb) {
        List<SearchResult> docs =
                search.search(tenantId, kb, "@RestController", 20);

        return docs.isEmpty()
                ? simple("Nenhum endpoint encontrado.", 1.0)
                : new CopilotAnswer(
                "Endpoints encontrados:",
                toSources(docs),
                confidence(docs),
                null,
                null
        );
    }

    // =========================================================
    // AUDITORIA
    // =========================================================
    private CopilotAnswer auditDto(String tenantId, String kb, String q) {
        String dto = extractDto(q);
        context = new ConversationContext(dto, null, null);

        List<SearchResult> usages =
                repository.findUsagesByClassName(tenantId, kb, dto);

        return simple(
                "DTO `" + dto + "` possui " + usages.size() + " usos.",
                1.0
        );
    }

    // =========================================================
    // FALLBACK
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

    private double confidence(List<SearchResult> docs) {
        return docs.stream()
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0);
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
