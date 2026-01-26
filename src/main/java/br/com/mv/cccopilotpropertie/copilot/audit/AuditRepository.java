package br.com.mv.cccopilotpropertie.copilot.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditRepository
        extends JpaRepository<AuditExecution, Long> {

    List<AuditExecution> findByDtoName(String dtoName);

    List<AuditExecution> findByKnowledgeBase(String knowledgeBase);

    long countByRiskLevel(String riskLevel);


    @Query("""
    select a.dtoName, count(a),
           sum(case when a.alertLevel = 'CRITICAL' then 1 else 0 end),
           sum(case when a.riskLevel = 'ALTO' then 1 else 0 end)
    from AuditExecution a
    group by a.dtoName
    order by count(a) desc
""")
    List<Object[]> aggregateByDto();

    @Query("""
    select a.knowledgeBase, count(a),
           sum(case when a.alertLevel = 'CRITICAL' then 1 else 0 end),
           sum(case when a.riskLevel = 'ALTO' then 1 else 0 end)
    from AuditExecution a
    group by a.knowledgeBase
""")
    List<Object[]> aggregateByProject();

}
