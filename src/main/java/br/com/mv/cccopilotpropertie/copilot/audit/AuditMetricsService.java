package br.com.mv.cccopilotpropertie.copilot.audit;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditMetricsService {

    private final AuditRepository repository;

    public AuditMetricsService(AuditRepository repository) {
        this.repository = repository;
    }

    public List<AuditSummary> topDtos(String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "default";
        return repository.aggregateByDto(effectiveTenant)
                .stream()
                .map(this::map)
                .toList();
    }

    public List<AuditSummary> byProject(String tenantId) {
        String effectiveTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "default";
        return repository.aggregateByProject(effectiveTenant)
                .stream()
                .map(this::map)
                .toList();
    }

    public List<AuditSummary> topDtos() {
        return topDtos("default");
    }

    public List<AuditSummary> byProject() {
        return byProject("default");
    }

    private AuditSummary map(Object[] row) {
        return new AuditSummary(
                row[0] != null ? row[0].toString() : "DESCONHECIDO",
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3])
        );
    }

    private Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
