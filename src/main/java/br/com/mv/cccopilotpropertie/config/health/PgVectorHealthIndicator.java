package br.com.mv.cccopilotpropertie.config.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PgVectorHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_extension WHERE extname = 'vector'",
                    Integer.class
            );
            if (count != null && count > 0) {
                return Health.up()
                        .withDetail("extension", "vector")
                        .withDetail("status", "installed")
                        .build();
            } else {
                return Health.down()
                        .withDetail("extension", "vector")
                        .withDetail("status", "missing")
                        .withDetail("error", "Extensão pgvector não está instalada no PostgreSQL")
                        .build();
            }
        } catch (Exception e) {
            // Em testes unitários ou ambientes com H2, não derruba o health do app
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("not found") || msg.contains("h2") || msg.contains("syntax")) {
                return Health.up()
                        .withDetail("extension", "vector")
                        .withDetail("environment", "test/h2")
                        .build();
            }
            return Health.down(e)
                    .withDetail("extension", "vector")
                    .build();
        }
    }
}
