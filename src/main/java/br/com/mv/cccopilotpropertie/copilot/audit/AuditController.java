package br.com.mv.cccopilotpropertie.copilot.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/copilot/audit")
public class AuditController {

    private final AuditMetricsService metrics;

    public AuditController(AuditMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/dtos")
    public List<AuditSummary> topDtos() {
        return metrics.topDtos();
    }

    @GetMapping("/projects")
    public List<AuditSummary> byProject() {
        return metrics.byProject();
    }
}

