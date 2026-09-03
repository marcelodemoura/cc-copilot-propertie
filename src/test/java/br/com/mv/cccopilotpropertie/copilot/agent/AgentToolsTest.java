package br.com.mv.cccopilotpropertie.copilot.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolsTest {

    @Test
    void shouldGenerateAllToolSpecifications() {
        AgentTools tools = new AgentTools();
        List<Map<String, Object>> all = tools.all();

        assertEquals(7, all.size());

        List<String> names = all.stream()
                .map(m -> (Map<String, Object>) m.get("function"))
                .map(f -> (String) f.get("name"))
                .toList();

        assertTrue(names.contains("search_code"));
        assertTrue(names.contains("find_dto_definition"));
        assertTrue(names.contains("find_dto_usages"));
        assertTrue(names.contains("find_endpoints_using_dto"));
        assertTrue(names.contains("find_external_usages"));
        assertTrue(names.contains("analyze_breaking_change"));
        assertTrue(names.contains("audit_dto"));
    }
}
