package br.com.mv.cccopilotpropertie.copilot.audit;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditMetricsService {

    private final AuditRepository repository;

    public AuditMetricsService(AuditRepository repository) {
        this.repository = repository;
    }

    public List<AuditSummary> topDtos() {
        return repository.aggregateByDto()
                .stream()
                .map(this::map)
                .toList();
    }

    public List<AuditSummary> byProject() {
        return repository.aggregateByProject()
                .stream()
                .map(this::map)
                .toList();
    }

    private AuditSummary map(Object[] row) {
        return new AuditSummary(
                (String) row[0],
                (Long) row[1],
                (Long) row[2],
                (Long) row[3]
        );
    }
}
