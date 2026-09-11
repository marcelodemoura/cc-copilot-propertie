package br.com.mv.cccopilotpropertie.config;

import br.com.mv.cccopilotpropertie.config.health.LlmProviderHealthIndicator;
import br.com.mv.cccopilotpropertie.config.health.PgVectorHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthIndicatorsTest {

    @Test
    void pgVectorShouldReportUpWhenInstalled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        PgVectorHealthIndicator indicator = new PgVectorHealthIndicator(jdbc);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("vector", health.getDetails().get("extension"));
        assertEquals("installed", health.getDetails().get("status"));
    }

    @Test
    void pgVectorShouldReportDownWhenMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        PgVectorHealthIndicator indicator = new PgVectorHealthIndicator(jdbc);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("missing", health.getDetails().get("status"));
    }

    @Test
    void llmIndicatorShouldReportUpForMockProfile() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"mock"});

        LlmProviderHealthIndicator indicator = new LlmProviderHealthIndicator(
                env, "", "gpt-4o-mini", "http://localhost:11434", "qwen2.5-coder"
        );
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("mock", health.getDetails().get("provider"));
    }

    @Test
    void llmIndicatorShouldReportDownWhenOpenAiKeyMissing() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"openai"});

        LlmProviderHealthIndicator indicator = new LlmProviderHealthIndicator(
                env, "", "gpt-4o-mini", "http://localhost:11434", "qwen2.5-coder"
        );
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("missing_api_key", health.getDetails().get("status"));
    }
}
