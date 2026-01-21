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
        var docs = search.search(tenantId, knowledgeBase, question, 12);
        double confidence = calculateConfidence(docs);

        if (docs.isEmpty() || confidence < 0.15) {
            return new CopilotAnswer(
                    "Não encontrei informações suficientes na base de conhecimento.",
                    List.of(),
                    confidence
            );
        }

        // 🧬 Herança consciente
        var enrichedDocs = enrichWithInheritance(
                tenantId,
                knowledgeBase,
                docs
        );

        // 🚨 MODO AUDITOR
        if (isAuditQuestion(question)) {

            Optional<String> dtoOpt = extractDtoName(question);
            if (dtoOpt.isEmpty()) {
                return new CopilotAnswer(
                        "Não foi possível identificar o DTO a ser auditado.",
                        List.of(),
                        confidence
                );
            }

            String dto = dtoOpt.get();

            // 🌍 Busca global do contrato do DTO
            Optional<SearchResult> globalDto =
                    searchRepository.findDtoDefinitionGlobal(dto);

            if (globalDto.isEmpty()) {
                return new CopilotAnswer(
                        "O DTO " + dto + " não foi encontrado em nenhum projeto indexado.",
                        List.of(),
                        0.0
                );
            }

            return auditDtoUsage(
                    tenantId,
                    knowledgeBase,
                    dto,
                    enrichedDocs,
                    confidence
            );
        }

        // 🔗 MODO NORMAL (uso / explicação)
        UsageContext usageContext = enrichWithUsages(
                tenantId,
                knowledgeBase,
                question,
                enrichedDocs
        );

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
                confidence
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
                        ).stream()
                        .filter(u -> !u.path().contains("InfoDTO"))
                        .toList();

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
    // 🚨 AUDITORIA
    // =========================================================

    private CopilotAnswer auditDtoUsage(
            String tenantId,
            String knowledgeBase,
            String dto,
            List<SearchResult> docs,
            double confidence
    ) {
        SearchResult dtoDef = docs.get(0);
        String dtoContent = dtoDef.content();

        List<SearchResult> usages =
                searchRepository.findUsagesByClassName(
                                tenantId,
                                knowledgeBase,
                                dto
                        ).stream()
                        .filter(u -> !u.path().contains("InfoDTO"))
                        .toList();

        StringBuilder audit = new StringBuilder();
        audit.append("Auditoria do uso do ").append(dto).append(":\n\n");

        // 1️⃣ Campos obrigatórios
        audit.append("Campos obrigatórios definidos:\n");
        if (dtoContent.contains("@NotNull") || dtoContent.contains("@NotBlank")) {
            audit.append("- O DTO possui campos obrigatórios definidos por anotações.\n");
        } else {
            audit.append("- Não há campos obrigatórios definidos diretamente no DTO.\n");
        }

        // 2️⃣ Herança
        audit.append("\nHerança:\n");
        if (dtoContent.contains("extends ")) {
            audit.append("- O DTO estende outra classe.\n");
            audit.append("- Não há evidência de campos obrigatórios anotados na superclasse.\n");
        } else {
            audit.append("- O DTO não possui herança.\n");
        }

        // 3️⃣ Uso
        audit.append("\nAnálise de uso:\n");
        if (usages.isEmpty()) {
            audit.append("⚠️ Nenhum uso explícito do DTO foi encontrado.\n");
        } else {
            usages.forEach(u ->    {
                audit.append("- ")
                        .append(classifyUsage(u.path()))
                        .append(": ")
                        .append(u.path())
                        .append("\n")
                        .append("  ⚠️ Não há evidência clara de validação ou preenchimento explícito.\n");
            });
        }

        // 4️⃣ Conclusão
        audit.append("\nConclusão:\n");
        audit.append("⚠️ Existe risco potencial de violação de contrato ")
                .append("caso o DTO seja utilizado sem validação explícita.\n");

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
                || q.contains("riscos")
                || q.contains("respeita")
                || q.contains("correto")
                || q.contains("validado")
                || q.contains("auditoria");
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
    ) {}
}