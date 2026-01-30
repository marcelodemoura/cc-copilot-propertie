package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.alert.*;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;

import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.tomcat.util.IntrospectionUtils.capitalize;

@Service
public class RagService {

    private final SearchService search;
    private final SearchRepository searchRepository;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;
    private final AlertService alertService;
    private final CiEnforcer ciEnforcer;
    private final AuditService auditService;
    private final BreakingChangeAnalyzer breakingChangeAnalyzer;

    private ConversationContext lastContext;

    private static final Pattern DTO_PATTERN =
            Pattern.compile("(\\w+)\\s*DTO", Pattern.CASE_INSENSITIVE);

    private static final Pattern EXTENDS_PATTERN =
            Pattern.compile("extends\\s+(\\w+)");

    public RagService(
            SearchService search,
            SearchRepository searchRepository,
            PromptAssembler promptAssembler,
            AnswerService answer,
            AlertService alertService,
            CiEnforcer ciEnforcer,
            AuditService auditService,
            BreakingChangeAnalyzer breakingChangeAnalyzer
    ) {
        this.search = search;
        this.searchRepository = searchRepository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
        this.alertService = alertService;
        this.ciEnforcer = ciEnforcer;
        this.auditService = auditService;
        this.breakingChangeAnalyzer = breakingChangeAnalyzer;
    }

    // =========================================================
    // 🚀 ENTRYPOINT
    // =========================================================
    public CopilotAnswer ask(String tenantId, String knowledgeBase, String question) {

        // 🧠 PASSO 11 — entendimento do projeto
        if (isProjectUnderstandingQuestion(question)) {
            List<SearchResult> projectDocs =
                    search.search(tenantId, knowledgeBase, "DTO", 20);

            if (projectDocs.isEmpty()) {
                return simpleAnswer(
                        "Não há código suficiente indexado para entender o projeto.",
                        0.0
                );
            }

            return summarizeProject(
                    question,
                    projectDocs,
                    calculateConfidence(projectDocs)
            );
        }

        List<SearchResult> docs =
                search.search(tenantId, knowledgeBase, question, 12);

        if (docs.isEmpty()) {
            return emptyAnswer();
        }

        double confidence = calculateConfidence(docs);

        List<SearchResult> enrichedDocs =
                enrichWithInheritance(tenantId, knowledgeBase, docs);

        // 💥 PASSO 15 — BREAKING CHANGE
        if (isBreakingChangeQuestion(question)) {
            return analyzeBreakingChange(
                    tenantId,
                    knowledgeBase,
                    confidence
            );

        }

        // 🚨 Auditoria DTO
        if (isAuditQuestion(question)) {
            return handleAudit(tenantId, knowledgeBase, question, confidence);
        }

        // 📍 Campo — localização
        if (isFieldLocationQuestion(question)) {
            return locateField(question, enrichedDocs, confidence);
        }

        // 🔍 Campo — uso
        if (isFieldUsageQuestion(question)) {
            return locateFieldUsages(question, enrichedDocs, confidence);
        }

        // 🧹 Campo — remoção
        if (isFieldRemovalQuestion(question)) {
            return canRemoveField(question, enrichedDocs, confidence);
        }

        // 🌐 ENDPOINTS — PASSO 13
        if (isEndpointQuestion(question)) {
            return locateEndpoints(question, enrichedDocs, confidence);
        }

        // 🌍 Impacto externo — PASSO 14
        if (isExternalImpactQuestion(question)) {
            return analyzeExternalImpact(
                    tenantId,
                    knowledgeBase,
                    question,
                    enrichedDocs,
                    confidence
            );
        }

        return new CopilotAnswer(
                answer.ask(promptAssembler.build(question, enrichedDocs)),
                toSources(enrichedDocs),
                confidence,
                null,
                null
        );
    }

    private CopilotAnswer locateField(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        Optional<String> fieldOpt = extractFieldName(question);
        if (fieldOpt.isEmpty()) {
            return simpleAnswer("Não consegui identificar o campo.", confidence);
        }

        String field = fieldOpt.get();
        lastContext = new ConversationContext(null, field, null, null);

        List<SearchResult> matches = docs.stream()
                .filter(d -> d.content().contains(field))
                .toList();

        if (matches.isEmpty()) {
            return simpleAnswer(
                    "Não encontrei o campo `" + field + "`.",
                    confidence
            );
        }

        return new CopilotAnswer(
                "Campo `" + field + "` encontrado em:\n" +
                        matches.stream()
                                .map(m -> "- " + m.path())
                                .distinct()
                                .reduce("", (a, b) -> a + b + "\n"),
                toSources(matches),
                confidence,
                null,
                null
        );
    }

    private CopilotAnswer locateFieldUsages(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        Optional<String> fieldOpt = extractFieldName(question);
        if (fieldOpt.isEmpty()) {
            return simpleAnswer("Não consegui identificar o campo.", confidence);
        }

        String field = fieldOpt.get();
        lastContext = new ConversationContext(null, field, null, null);

        List<SearchResult> matches = docs.stream()
                .filter(d ->
                        d.content().contains("." + field)
                                || d.content().contains("get" + capitalize(field))
                )
                .toList();

        if (matches.isEmpty()) {
            return simpleAnswer(
                    "Não encontrei usos do campo `" + field + "`.",
                    confidence
            );
        }

        return new CopilotAnswer(
                "Usos do campo `" + field + "`:\n" +
                        matches.stream()
                                .map(m -> "- " + m.path())
                                .distinct()
                                .reduce("", (a, b) -> a + b + "\n"),
                toSources(matches),
                confidence,
                null,
                null
        );
    }

    private CopilotAnswer canRemoveField(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        Optional<String> fieldOpt = extractFieldName(question);
        if (fieldOpt.isEmpty()) {
            return simpleAnswer("Não consegui identificar o campo.", confidence);
        }

        String field = fieldOpt.get();

        // 🔍 Primeiro: identificar onde o campo aparece de verdade
        List<SearchResult> matches = docs.stream()
                .filter(d -> d.content().contains(field))
                .toList();

        // 🔎 PASSO 16.1 — inferir DTO a partir dos arquivos onde o campo aparece
        String inferredDto = matches.stream()
                .map(SearchResult::path)
                .filter(p -> p.endsWith("DTO.java"))
                .map(p -> p.substring(p.lastIndexOf("/") + 1))
                .map(p -> p.replace(".java", ""))
                .findFirst()
                .orElse(null);

        ChangeSet change = new ChangeSet(
                ChangeTarget.FIELD,
                ChangeType.REMOVE,
                field,
                inferredDto,   // 🔥 DTO AQUI
                null,
                null
        );


        // 🧠 salva contexto completo (FIELD → DTO)
        lastContext = new ConversationContext(
                inferredDto,
                field,
                null,
                change
        );

        if (matches.isEmpty()) {
            return simpleAnswer(
                    "O campo `" + field + "` não é usado. Remoção segura.",
                    confidence
            );
        }

        return new CopilotAnswer(
                "O campo `" + field + "` possui usos e pode gerar impacto.",
                toSources(matches),
                confidence,
                null,
                null
        );
    }

    private CopilotAnswer locateEndpoints(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        List<String> endpoints = docs.stream()
                .filter(d -> d.content().contains("@RequestMapping")
                        || d.content().contains("@GetMapping")
                        || d.content().contains("@PostMapping"))
                .map(SearchResult::path)
                .distinct()
                .toList();

        if (endpoints.isEmpty()) {
            return simpleAnswer(
                    "Nenhum endpoint REST encontrado.",
                    confidence
            );
        }

        return new CopilotAnswer(
                "Endpoints encontrados:\n" +
                        endpoints.stream()
                                .map(e -> "- " + e)
                                .reduce("", (a, b) -> a + b + "\n"),
                toSources(docs),
                confidence,
                null,
                null
        );
    }

    private CopilotAnswer analyzeExternalImpact(
            String tenantId,
            String knowledgeBase,
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        Optional<String> dtoOpt = extractDtoName(question);

        // 🔁 fallback: usar DTO do último ChangeSet
        if (dtoOpt.isEmpty()
                && lastContext != null
                && lastContext.lastChange() != null
                && lastContext.lastChange().dtoName() != null) {

            dtoOpt = Optional.of(lastContext.lastChange().dtoName());
        }

        if (dtoOpt.isEmpty()) {
            return simpleAnswer(
                    "Não consegui identificar o DTO para análise externa.",
                    confidence
            );
        }

        String dto = dtoOpt.get();

        // mantém contexto
        lastContext = new ConversationContext(dto, null, null, lastContext.lastChange());

        List<SearchResult> externalUsages =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId,
                        knowledgeBase,
                        dto
                );

        boolean contract = !externalUsages.isEmpty();

        return new CopilotAnswer(
                "DTO `" + dto + "` " +
                        (contract
                                ? "é usado como contrato externo."
                                : "não possui uso externo."),
                toSources(externalUsages),
                confidence,
                null,
                null
        );
    }



    // =========================================================
    // 💥 PASSO 15 — BREAKING CHANGE
    // =========================================================
    private CopilotAnswer analyzeBreakingChange(
            String tenantId,
            String knowledgeBase,
            double confidence
    ) {
        if (lastContext == null || lastContext.lastChange() == null) {
            return simpleAnswer(
                    "Nenhuma mudança registrada para análise de breaking change.",
                    confidence
            );
        }

        ChangeSet change = lastContext.lastChange();

        ImpactAnalysis impact =
                ImpactAnalysis.from(
                        tenantId,
                        knowledgeBase,
                        change,
                        searchRepository
                );

        BreakingAnalysisResult result =
                breakingChangeAnalyzer.analyze(change, impact);

        Optional<AlertResult> alert =
                alertService.evaluateBreaking(result);

        alert.ifPresent(ciEnforcer::enforce);

        // NÃO persistimos decisão ainda (entidade própria virá depois)
        auditService.recordChangeDecision(
                tenantId,
                knowledgeBase,
                change,
                result,
                alert.orElse(null)
        );

        String answer = """
                Análise de Breaking Change:
                
                • Elemento: %s
                • Tipo de mudança: %s
                • Classificação: %s
                • Motivo: %s
                • Versionamento necessário: %s
                """.formatted(
                change.elementName(),
                change.type(),
                result.breakingType(),
                result.reason(),
                result.requiresVersioning() ? "SIM" : "NÃO"
        );

        return new CopilotAnswer(
                answer,
                List.of(),
                confidence,
                null,
                alert.orElse(null)
        );
    }

    // =========================================================
    // 🚨 AUDITORIA DTO — PASSO 11–14
    // =========================================================
    private CopilotAnswer handleAudit(
            String tenantId,
            String knowledgeBase,
            String question,
            double confidence
    ) {
        Optional<String> dtoOpt = extractDtoName(question);

        if (dtoOpt.isEmpty()) {
            return simpleAnswer("Não identifiquei o DTO.", confidence);
        }

        String dto = dtoOpt.get();

        lastContext = new ConversationContext(dto, null, null, null);

        return searchRepository
                .findDtoDefinitionGlobal(tenantId, dto)
                .map(d -> auditDtoUsage(
                        tenantId,
                        knowledgeBase,
                        dto,
                        d,
                        confidence
                ))
                .orElse(simpleAnswer(
                        "O DTO " + dto + " não foi encontrado.",
                        confidence
                ));
    }

    // =========================================================
    // 🔎 AUDITORIA DE USO DO DTO
    // =========================================================
    private CopilotAnswer auditDtoUsage(
            String tenantId,
            String knowledgeBase,
            String dto,
            SearchResult globalDto,
            double confidence
    ) {
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

        boolean usedInOtherProjects = !externalUsages.isEmpty();
        int usageCount = usages.size() + externalUsages.size();

        String risk =
                usedInOtherProjects ? "ALTO"
                        : usageCount > 0 ? "MÉDIO"
                        : "BAIXO";

        DtoAuditResult audit = new DtoAuditResult(
                dto,
                risk,
                usedInOtherProjects,
                false,
                false,
                usageCount,
                List.of(),
                usedInOtherProjects
        );

        Optional<AlertResult> alert = alertService.evaluate(audit);
        alert.ifPresent(ciEnforcer::enforce);

        auditService.record(
                tenantId,
                knowledgeBase,
                audit,
                alert.orElse(null)
        );

        return new CopilotAnswer(
                "Auditoria do DTO `" + dto + "`\nRisco identificado: " + risk,
                toSources(Stream.concat(usages.stream(), externalUsages.stream()).toList()),
                confidence,
                audit,
                alert.orElse(null)
        );
    }

    // =========================================================
    // 🧠 PROJETO — PASSO 11
    // =========================================================
    private CopilotAnswer summarizeProject(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        String prompt = """
                Você é um arquiteto de software.
                Com base apenas no código fornecido, descreva:
                - objetivo do projeto
                - domínio de negócio
                - tipo (API, backend ou integração)
                """ + promptAssembler.build(question, docs);

        return new CopilotAnswer(
                answer.ask(prompt),
                toSources(docs),
                confidence,
                null,
                null
        );
    }

    // =========================================================
    // 🧠 CONTEXTO
    // =========================================================
    private record ConversationContext(
            String dto,
            String field,
            String endpoint,
            ChangeSet lastChange
    ) {
    }

    // =========================================================
    // 🔍 HELPERS
    // =========================================================
    private boolean isBreakingChangeQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("breaking")
                || q.contains("quebra")
                || q.contains("preciso versionar")
                || q.contains("compatível");
    }

    private Optional<String> extractDtoName(String q) {
        Matcher m = DTO_PATTERN.matcher(q);
        return m.find() ? Optional.of(m.group(1) + "DTO") : Optional.empty();
    }

    private Optional<String> extractFieldName(String q) {
        Matcher m = Pattern.compile("campo\\s+(\\w+)", Pattern.CASE_INSENSITIVE)
                .matcher(q);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private boolean isAuditQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("risco") || q.contains("auditoria");
    }

    private boolean isProjectUnderstandingQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("o que esse projeto faz")
                || q.contains("qual dominio")
                || q.contains("para que serve");
    }

    private boolean isFieldLocationQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("onde está o campo")
                || q.contains("onde fica o campo");
    }

    private boolean isFieldUsageQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("campo") && q.contains("usado");
    }

    private boolean isFieldRemovalQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("remover o campo")
                || q.contains("posso remover")
                || q.contains("qual impacto de remover")
                || q.contains("o que quebra se remover");
    }

    private boolean isEndpointQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("endpoint")
                || q.contains("api")
                || q.contains("rota")
                || q.contains("url")
                || q.contains("http");
    }

    private boolean isExternalImpactQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("impacta")
                || q.contains("contrato")
                || q.contains("externo")
                || q.contains("outro sistema");
    }

    private List<SearchResult> enrichWithInheritance(
            String tenant,
            String kb,
            List<SearchResult> docs
    ) {
        Matcher m = EXTENDS_PATTERN.matcher(docs.get(0).content());
        if (!m.find()) return docs;

        return searchRepository
                .findByClassName(tenant, kb, m.group(1))
                .map(p -> {
                    List<SearchResult> list = new ArrayList<>();
                    list.add(p);
                    list.addAll(docs);
                    return list;
                }).orElse(docs);
    }

    private double calculateConfidence(List<SearchResult> docs) {
        return docs.stream()
                .limit(3)
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0.0);
    }

    private List<CopilotAnswer.Source> toSources(List<SearchResult> docs) {
        return docs.stream()
                .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                .toList();
    }

    private CopilotAnswer simpleAnswer(String msg, double confidence) {
        return new CopilotAnswer(msg, List.of(), confidence, null, null);
    }

    private CopilotAnswer emptyAnswer() {
        return simpleAnswer(
                "Não encontrei informações suficientes na base.",
                0.0
        );
    }
}
