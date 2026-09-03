package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AgentToolExecutor {

    private final SearchService searchService;
    private final SearchRepository searchRepository;
    private final BreakingChangeAnalyzer breakingAnalyzer;
    private final AlertService alertService;
    private final AuditService auditService;

    public AgentToolExecutor(SearchService searchService,
                             SearchRepository searchRepository,
                             BreakingChangeAnalyzer breakingAnalyzer,
                             AlertService alertService,
                             AuditService auditService) {
        this.searchService = searchService;
        this.searchRepository = searchRepository;
        this.breakingAnalyzer = breakingAnalyzer;
        this.alertService = alertService;
        this.auditService = auditService;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public String execute(String toolName, String argsJson, String tenantId, String kb) {
        try {
            Map<String, Object> args = MAPPER.readValue(argsJson, MAP_TYPE);
            return switch (toolName) {
                case "search_code" -> searchCode(args, tenantId, kb);
                case "find_dto_definition" -> findDtoDefinition(args, tenantId, kb);
                case "find_dto_usages" -> findDtoUsages(args, tenantId, kb);
                case "find_endpoints_using_dto" -> findEndpoints(args, tenantId, kb);
                case "find_external_usages" -> findExternalUsages(args, tenantId, kb);
                case "analyze_breaking_change" -> analyzeBreaking(args, tenantId, kb);
                case "audit_dto" -> auditDto(args, tenantId, kb);
                default -> "Tool desconhecida: " + toolName;
            };
        } catch (Exception e) {
            return "Erro ao executar tool " + toolName + ": " + e.getMessage();
        }
    }

    private String searchCode(Map<String, Object> args, String tenantId, String kb) {
        String query = (String) args.get("query");
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 12;
        List<SearchResult> results = searchService.search(tenantId, kb, query, limit);
        return formatResults(results);
    }

    private String findDtoDefinition(Map<String, Object> args, String tenantId, String kb) {
        String className = (String) args.get("className");
        return searchRepository.findByClassName(tenantId, kb, className)
                .map(r -> "ARQUIVO: " + r.path() + "\n" + r.content())
                .orElse("Definição de " + className + " não encontrada.");
    }

    private String findDtoUsages(Map<String, Object> args, String tenantId, String kb) {
        String className = (String) args.get("className");
        List<SearchResult> results = searchRepository.findUsagesByClassName(tenantId, kb, className);
        return results.isEmpty() ? "Nenhum uso encontrado para " + className + "." : formatResults(results);
    }

    private String findEndpoints(Map<String, Object> args, String tenantId, String kb) {
        String dtoName = (String) args.get("dtoName");
        List<SearchResult> results = searchRepository.findEndpointsUsingDto(tenantId, kb, dtoName);
        return results.isEmpty() ? "Nenhum endpoint usa " + dtoName + "." : formatResults(results);
    }

    private String findExternalUsages(Map<String, Object> args, String tenantId, String kb) {
        String dtoName = (String) args.get("dtoName");
        List<SearchResult> results = searchRepository.findUsagesInOtherKnowledgeBases(tenantId, kb, dtoName);
        return results.isEmpty() ? dtoName + " não é usado em outros sistemas." : formatResults(results);
    }

    private String analyzeBreaking(Map<String, Object> args, String tenantId, String kb) {
        String fieldName = (String) args.get("fieldName");
        String dtoName = (String) args.get("dtoName");

        ChangeSet change = new ChangeSet(ChangeTarget.FIELD, ChangeType.REMOVE, fieldName, dtoName, null, null);
        ImpactAnalysis impact = ImpactAnalysis.from(tenantId, kb, change, searchRepository);
        BreakingAnalysisResult result = breakingAnalyzer.analyze(change, impact);

        return """
                Campo: %s | DTO: %s
                Classificação: %s
                Motivo: %s
                Requer versionamento: %s
                Uso interno: %s | Quebra contrato HTTP: %s
                """.formatted(
                fieldName, dtoName,
                result.breakingType(),
                result.reason(),
                result.requiresVersioning() ? "SIM" : "NÃO",
                impact.internalUsage() ? "SIM" : "NÃO",
                impact.breaksHttpContract() ? "SIM" : "NÃO"
        );
    }

    private String auditDto(Map<String, Object> args, String tenantId, String kb) {
        String dtoName = (String) args.get("dtoName");

        List<SearchResult> usages = searchRepository.findUsagesByClassName(tenantId, kb, dtoName);
        List<SearchResult> endpoints = searchRepository.findEndpointsUsingDto(tenantId, kb, dtoName);
        List<SearchResult> external = searchRepository.findUsagesInOtherKnowledgeBases(tenantId, kb, dtoName);
        String definition = searchRepository.findByClassName(tenantId, kb, dtoName)
                .map(SearchResult::content).orElse("");

        boolean contractDto = !endpoints.isEmpty() || !external.isEmpty();
        boolean hasValidation = definition.contains("@NotNull") || definition.contains("@NotBlank") || definition.contains("@Valid");
        String risk = contractDto && !hasValidation ? "ALTO" : contractDto || usages.size() > 3 ? "MÉDIO" : "BAIXO";

        List<String> recommendations = new ArrayList<>();
        if (contractDto) recommendations.add("Versione alterações incompatíveis e comunique os consumidores.");
        if (!hasValidation) recommendations.add("Adicione validações explícitas aos campos obrigatórios.");
        if (recommendations.isEmpty()) recommendations.add("Mantenha testes de contrato para este DTO.");

        DtoAuditResult audit = new DtoAuditResult(dtoName, risk, !external.isEmpty(), hasValidation, hasValidation,
                usages.size(), recommendations, contractDto);
        AlertResult alert = alertService.evaluate(audit).orElse(null);
        auditService.record(tenantId, kb, audit, alert);

        return """
                DTO: %s
                Risco: %s | Contrato externo: %s | Validações: %s
                Usos internos: %d | Endpoints: %d | Sistemas externos: %d
                Recomendações: %s
                Alerta: %s
                """.formatted(
                dtoName, risk,
                contractDto ? "SIM" : "NÃO",
                hasValidation ? "SIM" : "NÃO",
                usages.size(), endpoints.size(), external.size(),
                String.join("; ", recommendations),
                alert != null ? alert.level() + " — " + alert.message() : "nenhum"
        );
    }

    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SearchResult r : results) {
            sb.append("ARQUIVO: ").append(r.path()).append(" (score: ").append(String.format("%.2f", r.score())).append(")\n");
            sb.append(r.content(), 0, Math.min(500, r.content().length())).append("\n---\n");
        }
        return sb.toString();
    }

}
