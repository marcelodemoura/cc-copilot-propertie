package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.alert.*;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

@Service
public class RagService {

    private final SearchService search;
    private final SearchRepository searchRepository;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;
    private final AlertService alertService;
    private final CiEnforcer ciEnforcer;
    private final AuditService auditService;

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
            AuditService auditService
    ) {
        this.search = search;
        this.searchRepository = searchRepository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
        this.alertService = alertService;
        this.ciEnforcer = ciEnforcer;
        this.auditService = auditService;
    }

    // =========================================================
    // 🚀 ENTRYPOINT
    // =========================================================
    public CopilotAnswer ask(String tenantId, String knowledgeBase, String question) {

        // 🧠 PASSO 11 — entendimento do projeto (NÃO depende da pergunta)
        if (isProjectUnderstandingQuestion(question)) {

            List<SearchResult> projectDocs =
                    search.search(tenantId, knowledgeBase, "DTO", 20);

            if (projectDocs.isEmpty()) {
                return simpleAnswer(
                        "Não há código suficiente indexado para entender o projeto.",
                        0.0
                );
            }

            return summarizeProject(question, projectDocs, calculateConfidence(projectDocs));
        }

        List<SearchResult> docs =
                search.search(tenantId, knowledgeBase, question, 12);

        if (docs.isEmpty()) {
            return emptyAnswer();
        }

        double confidence = calculateConfidence(docs);

        List<SearchResult> enrichedDocs =
                enrichWithInheritance(tenantId, knowledgeBase, docs);

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

        // 🔗 Resposta normal
        return new CopilotAnswer(
                answer.ask(promptAssembler.build(question, enrichedDocs)),
                toSources(enrichedDocs),
                confidence,
                null,
                null
        );
    }

    // =========================================================
    // 📍 CAMPO — LOCALIZAÇÃO
    // =========================================================
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

        List<SearchResult> matches = docs.stream()
                .filter(d -> d.content().contains(field))
                .toList();

        if (matches.isEmpty()) {
            return simpleAnswer(
                    "Não encontrei o campo `" + field + "` nos arquivos indexados.",
                    confidence
            );
        }

        return new CopilotAnswer(
                "O campo `" + field + "` foi encontrado em:\n\n" +
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

    // =========================================================
    // 🔍 CAMPO — USO
    // =========================================================
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

        List<SearchResult> matches = docs.stream()
                .filter(d ->
                        d.content().contains("." + field)
                                || d.content().contains("get" + capitalize(field))
                )
                .toList();

        if (matches.isEmpty()) {
            return simpleAnswer(
                    "Não encontrei usos do campo `" + field + "` no projeto.",
                    confidence
            );
        }

        return new CopilotAnswer(
                "O campo `" + field + "` é utilizado em:\n\n" +
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

    // =========================================================
    // 🧹 CAMPO — REMOÇÃO (PASSO 13)
    // =========================================================
    private CopilotAnswer canRemoveField(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {
        Optional<String> fieldOpt = extractFieldName(question);

        if (fieldOpt.isEmpty()) {
            return simpleAnswer(
                    "Não foi possível identificar o campo na pergunta.",
                    confidence
            );
        }

        String field = fieldOpt.get();

        List<SearchResult> matches = docs.stream()
                .filter(d -> d.content().contains(field))
                .toList();

        if (matches.isEmpty()) {
            return simpleAnswer(
                    "O campo `" + field + "` não possui usos detectados. Pode ser removido com baixo risco.",
                    confidence
            );
        }

        List<SearchResult> realUsages = matches.stream()
                .filter(d ->
                        !d.content().matches("(?s).*private\\s+.*\\s+" + field + ".*")
                )
                .toList();

        if (realUsages.isEmpty()) {
            return new CopilotAnswer(
                    "O campo `" + field + "` está apenas declarado (ex: DTO). " +
                            "Não há usos em código. Remoção com baixo risco.",
                    toSources(matches),
                    confidence,
                    null,
                    null
            );
        }

        return new CopilotAnswer(
                "O campo `" + field + "` possui usos no projeto. A remoção pode causar impacto.\n\n" +
                        realUsages.stream()
                                .map(r -> "- " + r.path())
                                .distinct()
                                .reduce("", (a, b) -> a + b + "\n"),
                toSources(realUsages),
                confidence,
                null,
                null
        );
    }

    // =========================================================
    // 🚨 AUDITORIA DTO
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

    private CopilotAnswer auditDtoUsage(
            String tenantId,
            String knowledgeBase,
            String dto,
            SearchResult globalDto,
            double confidence
    ) {

        List<SearchResult> usages =
                searchRepository.findUsagesByClassName(tenantId, knowledgeBase, dto);

        List<SearchResult> externalUsages =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId, knowledgeBase, dto
                );

        boolean usedInOtherProjects = !externalUsages.isEmpty();
        int usageCount = usages.size() + externalUsages.size();

        String risk = usedInOtherProjects ? "ALTO" : usageCount > 0 ? "MÉDIO" : "BAIXO";

        DtoAuditResult structured = new DtoAuditResult(
                dto,
                risk,
                usedInOtherProjects,
                false,
                false,
                usageCount,
                List.of(),
                usedInOtherProjects
        );

        Optional<AlertResult> alert = alertService.evaluate(structured);
        alert.ifPresent(ciEnforcer::enforce);

        auditService.record(
                tenantId,
                knowledgeBase,
                structured,
                alert.orElse(null)
        );

        return new CopilotAnswer(
                "Auditoria do uso do " + dto + ":\n\nRisco identificado: " + risk,
                toSources(Stream.concat(usages.stream(), externalUsages.stream()).toList()),
                confidence,
                structured,
                alert.orElse(null)
        );
    }

    // =========================================================
    // 🧠 PROJETO
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
    // 🔍 HELPERS
    // =========================================================
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
        return simpleAnswer("Não encontrei informações suficientes na base.", 0.0);
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
