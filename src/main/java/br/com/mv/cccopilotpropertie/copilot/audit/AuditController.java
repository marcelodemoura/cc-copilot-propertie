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
    @Operation(summary = "DTOs mais auditados", description = "Retorna os DTOs com mais execuções de auditoria para o tenant, ordenados por frequência.")
    public List<AuditSummary> topDtos(@RequestParam(value = "tenantId", defaultValue = "default") String tenantId) {
        return metrics.topDtos(tenantId);
    }

    @GetMapping("/projects")
    @Operation(summary = "Auditoria por projeto", description = "Retorna métricas de auditoria agrupadas por projeto para o tenant.")
    public List<AuditSummary> byProject(@RequestParam(value = "tenantId", defaultValue = "default") String tenantId) {
        return metrics.byProject(tenantId);
    }
}

