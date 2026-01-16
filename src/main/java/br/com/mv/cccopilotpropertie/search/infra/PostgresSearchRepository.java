package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresSearchRepository implements SearchRepository {

    private final JdbcTemplate jdbc;

    public PostgresSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchResult> search(
            String tenantId,
            String knowledgeBase,
            float[] vector,
            int limit
    ) {

        return jdbc.query("""
            SELECT
                path,
                content,
                1 - (embedding <=> ?::vector) AS score
            FROM code_embeddings
            WHERE tenant_id = ?
              AND knowledge_base = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
        """,
                ps -> {
                    ps.setObject(1, vector);
                    ps.setString(2, tenantId);
                    ps.setString(3, knowledgeBase);
                    ps.setObject(4, vector);
                    ps.setInt(5, limit);
                },
                (rs, i) -> new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ));
    }

    // 🔴 NOVO — usado pela herança consciente
    @Override
    public Optional<SearchResult> findByClassName(
            String tenantId,
            String knowledgeBase,
            String className
    ) {
        return jdbc.query("""
            SELECT
                path,
                content,
                1.0 AS score
            FROM code_embeddings
            WHERE tenant_id = ?
              AND knowledge_base = ?
              AND path LIKE '%' || ? || '.java'
            LIMIT 1
        """,
                rs -> {
                    if (rs.next()) {
                        return Optional.of(
                                new SearchResult(
                                        rs.getString("path"),
                                        rs.getString("content"),
                                        rs.getDouble("score")
                                )
                        );
                    }
                    return Optional.empty();
                },
                tenantId,
                knowledgeBase,
                className
        );
    }
}
