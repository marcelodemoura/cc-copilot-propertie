package br.com.mv.cccopilotpropertie.copilot.agent;

import br.com.mv.cccopilotpropertie.copilot.alert.AlertResult;
import br.com.mv.cccopilotpropertie.copilot.alert.AlertService;
import br.com.mv.cccopilotpropertie.copilot.audit.AuditService;
import br.com.mv.cccopilotpropertie.copilot.breaking.*;
import br.com.mv.cccopilotpropertie.copilot.domain.DtoAuditResult;
import br.com.mv.cccopilotpropertie.search.application.SearchService;
import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import br.com.mv.cccopilotpropertie.search.infra.SearchRepository;
import br.com.mv.cccopilotpropertie.project.infra.ProjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentToolExecutor {

    private final SearchService searchService;
    private final SearchRepository searchRepository;
    private final BreakingChangeAnalyzer breakingAnalyzer;
    private final AlertService alertService;
    private final AuditService auditService;
    private final ProjectRepository projectRepository;
    private final String allowedBasePath;

    public AgentToolExecutor(SearchService searchService,
                             SearchRepository searchRepository,
                             BreakingChangeAnalyzer breakingAnalyzer,
                             AlertService alertService,
                             AuditService auditService) {
        this(searchService, searchRepository, breakingAnalyzer, alertService, auditService, null, ".");
    }

    @Autowired
    public AgentToolExecutor(SearchService searchService,
                             SearchRepository searchRepository,
                             BreakingChangeAnalyzer breakingAnalyzer,
                             AlertService alertService,
                             AuditService auditService,
                             @Autowired(required = false) ProjectRepository projectRepository,
                             @Value("${indexer.base-path:.}") String allowedBasePath) {
        this.searchService = searchService;
        this.searchRepository = searchRepository;
        this.breakingAnalyzer = breakingAnalyzer;
        this.alertService = alertService;
        this.auditService = auditService;
        this.projectRepository = projectRepository;
        this.allowedBasePath = allowedBasePath != null ? allowedBasePath : ".";
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public String execute(String toolName, String argsJson, String tenantId, String kb) {
        try {
            Map<String, Object> args = (argsJson != null && !argsJson.isBlank())
                    ? MAPPER.readValue(argsJson, MAP_TYPE)
                    : Map.of();
            return switch (toolName) {
                case "search_code" -> searchCode(args, tenantId, kb);
                case "find_dto_definition" -> findDtoDefinition(args, tenantId, kb);
                case "find_dto_usages" -> findDtoUsages(args, tenantId, kb);
                case "find_endpoints_using_dto" -> findEndpoints(args, tenantId, kb);
                case "find_external_usages" -> findExternalUsages(args, tenantId, kb);
                case "analyze_breaking_change" -> analyzeBreaking(args, tenantId, kb);
                case "audit_dto" -> auditDto(args, tenantId, kb);
                case "read_file" -> readFile(args, tenantId, kb);
                case "list_files" -> listFiles(args, tenantId, kb);
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
        String typeStr = (String) args.getOrDefault("changeType", "REMOVE");
        String targetStr = (String) args.getOrDefault("target", "FIELD");
        Object beforeState = args.get("beforeState");
        Object afterState = args.get("afterState");

        ChangeType changeType;
        try {
            changeType = ChangeType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            changeType = ChangeType.REMOVE;
        }

        ChangeTarget changeTarget;
        try {
            changeTarget = ChangeTarget.valueOf(targetStr.toUpperCase());
        } catch (Exception e) {
            changeTarget = ChangeTarget.FIELD;
        }

        ChangeSet change = new ChangeSet(changeTarget, changeType, fieldName, dtoName, beforeState, afterState);
        ImpactAnalysis impact = ImpactAnalysis.from(tenantId, kb, change, searchRepository);
        BreakingAnalysisResult result = breakingAnalyzer.analyze(change, impact);
        AlertResult alert = alertService.evaluateBreaking(result).orElse(null);

        // Registra decisão arquitetural na auditoria
        auditService.recordChangeDecision(tenantId, kb, change, result, alert);

        return """
                Campo: %s | DTO: %s
                Classificação: %s
                Motivo: %s
                Requer versionamento: %s
                Uso interno: %s | Quebra contrato HTTP: %s
                Alerta: %s
                """.formatted(
                fieldName, dtoName,
                result.breakingType(),
                result.reason(),
                result.requiresVersioning() ? "SIM" : "NÃO",
                impact.internalUsage() ? "SIM" : "NÃO",
                impact.breaksHttpContract() ? "SIM" : "NÃO",
                alert != null ? alert.level() + " — " + alert.message() : "nenhum"
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

    private String readFile(Map<String, Object> args, String tenantId, String kb) {
        String rawPath = (String) args.get("path");
        if (rawPath == null || rawPath.isBlank()) {
            return "Erro: Parâmetro 'path' é obrigatório.";
        }

        Path projectRoot = resolveProjectPath(kb);
        Path target = Path.of(rawPath);
        if (!target.isAbsolute()) {
            target = projectRoot.resolve(target);
        }
        target = target.normalize().toAbsolutePath();

        if (!Files.exists(target) || Files.isDirectory(target)) {
            return "Arquivo não encontrado ou é um diretório: " + rawPath;
        }

        try {
            Path realTarget = target.toRealPath();
            Path realProjectRoot = Files.exists(projectRoot) ? projectRoot.toRealPath() : projectRoot;
            Path allowed = Path.of(allowedBasePath).normalize().toAbsolutePath();
            Path realAllowed = Files.exists(allowed) ? allowed.toRealPath() : allowed;

            if (!realTarget.startsWith(realProjectRoot) && !realTarget.startsWith(realAllowed)) {
                return "Acesso negado: O arquivo está fora do diretório do projeto: " + target;
            }

            List<String> lines = Files.readAllLines(realTarget, StandardCharsets.UTF_8);
            int totalLines = lines.size();

            int start = args.containsKey("startLine") && args.get("startLine") != null
                    ? Math.max(1, ((Number) args.get("startLine")).intValue())
                    : 1;
            int end = args.containsKey("endLine") && args.get("endLine") != null
                    ? Math.min(totalLines, ((Number) args.get("endLine")).intValue())
                    : Math.min(totalLines, start + 250);

            if (start > totalLines) {
                return "O arquivo possui apenas " + totalLines + " linhas.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ARQUIVO: ").append(realTarget).append(" (linhas ").append(start).append(" a ").append(end).append(" de ").append(totalLines).append(")\n");
            for (int i = start - 1; i < end; i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Erro ao ler arquivo " + rawPath + ": " + e.getMessage();
        }
    }

    private static final java.util.Set<String> IGNORED_DIRS = java.util.Set.of(
            ".git", ".gradle", "build", "target", ".idea", ".run", ".github", ".sixth",
            "log", "logs", "node_modules", "dist", "out"
    );

    private String listFiles(Map<String, Object> args, String tenantId, String kb) {
        String subPath = args.containsKey("path") && args.get("path") != null ? (String) args.get("path") : "";
        int maxDepth = args.containsKey("maxDepth") && args.get("maxDepth") != null
                ? Math.min(5, Math.max(1, ((Number) args.get("maxDepth")).intValue()))
                : 4;

        Path projectRoot = resolveProjectPath(kb);
        Path target = projectRoot.resolve(subPath).normalize().toAbsolutePath();

        if (!Files.exists(target) || !Files.isDirectory(target)) {
            return "Diretório não encontrado: " + subPath;
        }

        try {
            Path realTarget = target.toRealPath();
            Path realProjectRoot = Files.exists(projectRoot) ? projectRoot.toRealPath() : projectRoot;
            Path allowed = Path.of(allowedBasePath).normalize().toAbsolutePath();
            Path realAllowed = Files.exists(allowed) ? allowed.toRealPath() : allowed;

            if (!realTarget.startsWith(realProjectRoot) && !realTarget.startsWith(realAllowed)) {
                return "Acesso negado: Diretório fora do projeto: " + target;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ÁRVORE DE ARQUIVOS: ").append(target).append(" (profundidade: ").append(maxDepth).append(")\n");
            List<String> treeLines = new ArrayList<>();
            buildTree(target, maxDepth, 1, treeLines);
            for (String line : treeLines) {
                sb.append(line).append("\n");
            }
            if (treeLines.isEmpty()) {
                sb.append("(diretório vazio ou todos os arquivos estão em pastas ignoradas)\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Erro ao listar diretório " + subPath + ": " + e.getMessage();
        }
    }

    private void buildTree(Path current, int maxDepth, int currentDepth, List<String> lines) {
        if (lines.size() >= 80) return;

        try (var stream = Files.list(current)) {
            List<Path> entries = stream.sorted(java.util.Comparator.comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase())).toList();

            for (Path entry : entries) {
                if (lines.size() >= 80) {
                    lines.add("... (listagem truncada em 80 itens)");
                    return;
                }

                String name = entry.getFileName().toString();
                if (IGNORED_DIRS.contains(name) || name.startsWith(".")) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    // Colapsa cadeias de diretórios de filho único (ex: br/com/mv/cpv)
                    Path curr = entry;
                    List<String> parts = new ArrayList<>();
                    parts.add(curr.getFileName().toString());
                    while (true) {
                        try (var subStream = Files.list(curr)) {
                            List<Path> validSubs = subStream.filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString())
                                    && !p.getFileName().toString().startsWith(".")).toList();
                            if (validSubs.size() == 1 && Files.isDirectory(validSubs.get(0))) {
                                curr = validSubs.get(0);
                                parts.add(curr.getFileName().toString());
                            } else {
                                break;
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }

                    String collapsedName = String.join("/", parts);
                    String indent = "  ".repeat(Math.max(0, currentDepth - 1));
                    lines.add(indent + "[DIR] " + collapsedName + "/");

                    if (currentDepth < maxDepth) {
                        buildTree(curr, maxDepth, currentDepth + 1, lines);
                    }
                } else {
                    String indent = "  ".repeat(Math.max(0, currentDepth - 1));
                    lines.add(indent + "[ARQ] " + name);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Path resolveProjectPath(String kb) {
        if (projectRepository != null && kb != null && !kb.isBlank()) {
            try {
                UUID id = UUID.fromString(kb);
                var project = projectRepository.findById(id);
                if (project.isPresent()) {
                    return Path.of(project.get().getRootPath()).toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
            }
        }
        return Path.of(allowedBasePath).toAbsolutePath().normalize();
    }
}
