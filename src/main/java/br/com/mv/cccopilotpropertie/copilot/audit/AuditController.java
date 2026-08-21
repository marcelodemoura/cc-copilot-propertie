package br.com.mv.cccopilotpropertie.copilot.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/copilot/audit")
@Tag(name = "Auditoria", description = "Métricas de auditoria de DTOs")
public class AuditController {

    private final AuditMetricsService metrics;

    public AuditController(AuditMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/dtos")
    @Operation(summary = "DTOs mais auditados", description = "Retorna os DTOs com mais execuções de auditoria, ordenados por frequência.")
    public List<AuditSummary> topDtos() {
        return metrics.topDtos();
    }

    @GetMapping("/projects")
    @Operation(summary = "Auditoria por projeto")
    public List<AuditSummary> byProject() {
        return metrics.byProject();
    }
}

