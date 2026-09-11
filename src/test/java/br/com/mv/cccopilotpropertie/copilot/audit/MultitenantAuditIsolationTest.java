package br.com.mv.cccopilotpropertie.copilot.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("mock")
@Transactional
class MultitenantAuditIsolationTest {

    @Autowired
    private AuditRepository auditRepository;

    @Test
    void shouldStrictlyIsolateAuditAggregationsByTenant() {
        // Dados do Tenant A
        auditRepository.save(new AuditExecution(
                "tenant-a", "proj-a", "ClienteDTO", "ALTO", true, false, 5, "CRITICAL"
        ));
        auditRepository.save(new AuditExecution(
                "tenant-a", "proj-a", "ClienteDTO", "BAIXO", false, true, 2, null
        ));

        // Dados do Tenant B
        auditRepository.save(new AuditExecution(
                "tenant-b", "proj-b", "PedidoDTO", "MÉDIO", true, true, 3, "WARNING"
        ));

        // Consulta Tenant A
        List<Object[]> aggA = auditRepository.aggregateByDto("tenant-a");
        assertEquals(1, aggA.size(), "Tenant A deve retornar apenas seus próprios DTOs");
        assertEquals("ClienteDTO", aggA.get(0)[0]);
        assertEquals(2L, ((Number) aggA.get(0)[1]).longValue());

        // Consulta Tenant B
        List<Object[]> aggB = auditRepository.aggregateByDto("tenant-b");
        assertEquals(1, aggB.size(), "Tenant B deve retornar apenas seus próprios DTOs");
        assertEquals("PedidoDTO", aggB.get(0)[0]);
        assertEquals(1L, ((Number) aggB.get(0)[1]).longValue());

        // Agregação por projeto no Tenant A
        List<Object[]> projA = auditRepository.aggregateByProject("tenant-a");
        assertEquals(1, projA.size());
        assertEquals("proj-a", projA.get(0)[0]);

        // Agregação por projeto no Tenant B
        List<Object[]> projB = auditRepository.aggregateByProject("tenant-b");
        assertEquals(1, projB.size());
        assertEquals("proj-b", projB.get(0)[0]);

        // Tenant C inexistente deve retornar vazio
        List<Object[]> aggC = auditRepository.aggregateByDto("tenant-c");
        assertTrue(aggC.isEmpty(), "Tenant sem registros deve retornar lista vazia");
    }
}
