package br.com.mv.cccopilotpropertie.copilot.rag.application;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertLevel;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.alert.CiEnforcer;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.copilot.policy.PolicyDecision;
import br.com.mv.cccopilotpropertie.copilot.policy.PolicyService;
import br.com.mv.cccopilotpropertie.copilot.policy.ProjectPolicy;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class RagService {

    private final SearchService search;
    private final SearchRepository searchRepository;
    private final PromptAssembler promptAssembler;
    private final AnswerService answer;
    private final AlertService alertService;
    private final CiEnforcer ciEnforcer;
    private final PolicyService policyService;
    private final ProjectPolicy projectPolicy;
    private static final Pattern EXTENDS_PATTERN =
            Pattern.compile("extends\\s+(\\w+)");
    private static final Pattern DTO_PATTERN =
            Pattern.compile("(\\w+)\\s*DTO", Pattern.CASE_INSENSITIVE);

    private ConversationContext lastContext;

    private final AuditService auditService;


    private record ConversationContext(String dto, String knowledgeBase) {
    }

    public RagService(
            SearchService search,
            SearchRepository searchRepository,
            PromptAssembler promptAssembler,
            AnswerService answer,
            AlertService alertService,
            CiEnforcer ciEnforcer, PolicyService policyService, ProjectPolicy projectPolicy, AuditService auditService
    ) {
        this.search = search;
        this.searchRepository = searchRepository;
        this.promptAssembler = promptAssembler;
        this.answer = answer;
        this.alertService = alertService;
        this.ciEnforcer = ciEnforcer;
        this.policyService = policyService;
        this.projectPolicy = projectPolicy;
        this.auditService = auditService;
    }

    // =========================================================
    // 🚀 ENTRYPOINT
    // =========================================================
    public CopilotAnswer ask(String tenantId, String knowledgeBase, String question) {

        if (isCanonicalQuestion(question)) {
            if (lastContext == null) {
                return new CopilotAnswer(
                        "Não há contexto anterior para avaliar.",
                        List.of(),
                        0.0,
                        null,
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
                    null,
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
        // 🧠 ENTENDIMENTO DO PROJETO (PASSO 11)
        // =====================================================
        if (isProjectUnderstandingQuestion(question)) {
            return summarizeProject(question, enrichedDocs, confidence);
        }


        // =====================================================
        // 🚨 AUDITORIA
        // =====================================================
        if (isAuditQuestion(question)) {

            if (dtoOpt.isEmpty()) {
                return new CopilotAnswer(
                        "Não foi possível identificar o DTO a ser auditado.",
                        List.of(),
                        confidence,
                        null,
                        null
                );
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
                    .orElseGet(() -> new CopilotAnswer(
                            "O DTO " + dto + " não foi encontrado em nenhum projeto indexado.",
                            List.of(),
                            0.0,
                            null,
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
                null,
                null
        );
    }

    // =========================================================
    // 🚨 AUDITORIA + SCORE + METADATA + CI
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
                        tenantId, knowledgeBase, dto);

        List<SearchResult> externalUsages =
                searchRepository.findUsagesInOtherKnowledgeBases(
                        tenantId, knowledgeBase, dto);

        boolean hasRequiredFields =
                dtoContent.contains("@NotNull") || dtoContent.contains("@NotBlank");

        boolean usedInOtherProjects = !externalUsages.isEmpty();

        // 🔖 METADATA EXPLÍCITA
        boolean explicitlyContract = dtoContent.contains("@ContractDto");
        boolean explicitlyInternal = dtoContent.contains("@InternalDto");

        boolean isContractDto;
        if (explicitlyContract) {
            isContractDto = true;
        } else if (explicitlyInternal) {
            isContractDto = false;
        } else {
            isContractDto = inferContractDto(usedInOtherProjects, usages);
        }

        boolean hasExplicitValidation =
                detectExplicitValidation(usages, externalUsages);

        int usageCount = usages.size() + externalUsages.size();

        // ⚠️ Regra forte: @InternalDto vazando = ALTO
        String risk;
        if (explicitlyInternal && usedInOtherProjects) {
            risk = "ALTO";
        } else if (isContractDto && hasRequiredFields && !hasExplicitValidation) {
            risk = "ALTO";
        } else if (hasRequiredFields && usageCount > 0) {
            risk = "MÉDIO";
        } else {
            risk = "BAIXO";
        }

        String audit = "Auditoria do uso do " + dto + ":\n\n" +
                "Risco identificado: " + risk + "\n";

        if (explicitlyContract) {
            audit += "• DTO marcado explicitamente como CONTRATO (@ContractDto)\n";
        }
        if (explicitlyInternal) {
            audit += "• DTO marcado explicitamente como INTERNO (@InternalDto)\n";
        }
        if (!explicitlyContract && !explicitlyInternal && isContractDto) {
            audit += "• DTO classificado como DTO DE CONTRATO (inferência)\n";
        }

        audit += hasExplicitValidation
                ? "• Validação explícita detectada no ponto de uso\n"
                : "• Nenhuma validação explícita detectada no ponto de uso\n";

        List<String> recommendations = new ArrayList<>(getRecommendations(risk));

        if (isContractDto) {
            recommendations.add("DTO é usado como contrato entre sistemas");
            recommendations.add("Considere versionar o DTO (ex: " + dto + "V1)");
        }

        if (explicitlyInternal && usedInOtherProjects) {
            recommendations.add("DTO marcado como interno está vazando entre sistemas");
        }

        DtoAuditResult structured = new DtoAuditResult(
                dto,
                risk,
                usedInOtherProjects,
                hasRequiredFields,
                hasExplicitValidation,
                usageCount,
                recommendations,
                isContractDto
        );


// ================================
// 🧭 POLICY
// ================================
        PolicyDecision decision =
                policyService.evaluate(
                        knowledgeBase,
                        structured,
                        projectPolicy
                );

        Optional<AlertResult> alert;

// Violação de policy gera alerta crítico
        if (!decision.allowed()) {
            alert = Optional.of(new AlertResult(
                    AlertLevel.CRITICAL,
                    "Violação de política do projeto",
                    decision.reason()
            ));
        } else {
            alert = alertService.evaluate(structured);
        }

// ================================
// 🚨 CI ENFORCER
// ================================
        alert.ifPresent(ciEnforcer::enforce);

// ================================
// 🔗 SOURCES
// ================================
        Map<String, CopilotAnswer.Source> uniqueSources = new LinkedHashMap<>();

        Stream.concat(usages.stream(), externalUsages.stream())
                .forEach(u ->
                        uniqueSources.put(
                                u.path(),
                                new CopilotAnswer.Source(u.path(), u.score())
                        )
                );

        List<CopilotAnswer.Source> sources =
                new ArrayList<>(uniqueSources.values());

        auditService.record(
                tenantId,
                knowledgeBase,
                structured,
                alert.orElse(null)
        );


// ================================
// 🔚 RETURN ÚNICO
// ================================
        return new CopilotAnswer(
                audit,
                sources,
                confidence,
                structured,
                alert.orElse(null)
        );


    }

    // =========================================================
    // 🔍 HELPERS
    // =========================================================
    private Optional<String> extractDtoName(String question) {
        Matcher m = DTO_PATTERN.matcher(question);
        return m.find() ? Optional.of(m.group(1) + "DTO") : Optional.empty();
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

    private boolean inferContractDto(boolean usedInOtherProjects, List<SearchResult> usages) {
        return usedInOtherProjects ||
                usages.stream().anyMatch(u -> {
                    String p = u.path().toLowerCase();
                    return p.contains("/controller")
                            || p.contains("/queue")
                            || p.contains("/producer")
                            || p.contains("/consumer");
                });
    }

    private boolean detectExplicitValidation(
            List<SearchResult> usages,
            List<SearchResult> externalUsages
    ) {
        return Stream.concat(usages.stream(), externalUsages.stream())
                .anyMatch(u -> {
                    String c = u.content();
                    return c.contains("@Valid") || c.contains("@Validated");
                });
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


    private double calculateConfidence(List<SearchResult> docs) {
        return docs.stream()
                .limit(3)
                .mapToDouble(SearchResult::score)
                .average()
                .orElse(0.0);
    }

    private List<SearchResult> enrichWithInheritance(
            String tenantId,
            String knowledgeBase,
            List<SearchResult> docs
    ) {
        if (docs.isEmpty()) return docs;

        SearchResult child = docs.get(0);
        Matcher matcher = EXTENDS_PATTERN.matcher(child.content());

        if (!matcher.find()) return docs;

        return searchRepository
                .findByClassName(tenantId, knowledgeBase, matcher.group(1))
                .map(parent -> {
                    List<SearchResult> enriched = new ArrayList<>();
                    enriched.add(parent);
                    enriched.addAll(docs);
                    return enriched;
                })
                .orElse(docs);
    }

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

    private String classifyUsage(String path) {
        String p = path.toLowerCase();
        if (p.contains("controller")) return "Controller";
        if (p.contains("service")) return "Service";
        if (p.contains("queue")) return "Mensageria";
        if (p.contains("producer")) return "Producer";
        if (p.contains("consumer")) return "Consumer";
        if (p.contains("/dto/")) return "DTO dependente";
        return "Outro";
    }

    private record UsageContext(String prompt, List<SearchResult> usages) {
    }

    private CopilotAnswer summarizeProject(
            String question,
            List<SearchResult> docs,
            double confidence
    ) {

        String prompt = """
                Você é um arquiteto de software.
                
                Com base APENAS no código fornecido (DTOs, pacotes, nomes de classes e filas),
                descreva em até 5 linhas:
                
                - Qual é o objetivo principal deste projeto
                - Qual domínio de negócio ele parece atender
                - Se ele atua como API, backend de domínio ou integração
                
                ⚠️ Não invente informações.
                ⚠️ Caso algo não esteja claro, deixe explícito que é uma inferência.
                
                CÓDIGO INDEXADO:
                """ + promptAssembler.build(question, docs);

        String response = answer.ask(prompt);

        List<CopilotAnswer.Source> sources =
                docs.stream()
                        .limit(10)
                        .map(d -> new CopilotAnswer.Source(d.path(), d.score()))
                        .toList();

        return new CopilotAnswer(
                response,
                sources,
                confidence,
                null,
                null
        );
    }

    private boolean isProjectUnderstandingQuestion(String q) {
        q = q.toLowerCase();
        return q.contains("o que esse projeto faz")
                || q.contains("qual o objetivo do projeto")
                || q.contains("qual a responsabilidade do projeto")
                || q.contains("qual o dominio do projeto")
                || q.contains("para que serve esse projeto");
    }


}
