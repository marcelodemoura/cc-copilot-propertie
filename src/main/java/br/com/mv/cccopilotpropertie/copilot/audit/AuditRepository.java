package br.com.mv.cccopilotpropertie.copilot.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditRepository
        extends JpaRepository<AuditExecution, Long> {

    List<AuditExecution> findByTenantIdAndDtoName(String tenantId, String dtoName);

    List<AuditExecution> findByTenantIdAndKnowledgeBase(String tenantId, String knowledgeBase);

    long countByTenantIdAndRiskLevel(String tenantId, String riskLevel);

    @Query("""
    select a.dtoName, count(a),
           coalesce(sum(case when a.alertLevel = 'CRITICAL' then 1L else 0L end), 0L),
           coalesce(sum(case when a.riskLevel = 'ALTO' then 1L else 0L end), 0L)
    from AuditExecution a
    where a.tenantId = :tenantId
    group by a.dtoName
    order by count(a) desc
""")
    List<Object[]> aggregateByDto(@Param("tenantId") String tenantId);

    @Query("""
    select a.knowledgeBase, count(a),
           coalesce(sum(case when a.alertLevel = 'CRITICAL' then 1L else 0L end), 0L),
           coalesce(sum(case when a.riskLevel = 'ALTO' then 1L else 0L end), 0L)
    from AuditExecution a
    where a.tenantId = :tenantId
    group by a.knowledgeBase
    order by count(a) desc
""")
    List<Object[]> aggregateByProject(@Param("tenantId") String tenantId);

    @Query("""
    select a.dtoName, count(a),
           coalesce(sum(case when a.alertLevel = 'CRITICAL' then 1L else 0L end), 0L),
           coalesce(sum(case when a.riskLevel = 'ALTO' then 1L else 0L end), 0L)
    from AuditExecution a
    group by a.dtoName
    order by count(a) desc
""")
    List<Object[]> aggregateByDto();

    @Query("""
    select a.knowledgeBase, count(a),
           coalesce(sum(case when a.alertLevel = 'CRITICAL' then 1L else 0L end), 0L),
           coalesce(sum(case when a.riskLevel = 'ALTO' then 1L else 0L end), 0L)
    from AuditExecution a
    group by a.knowledgeBase
    order by count(a) desc
""")
    List<Object[]> aggregateByProject();
}
