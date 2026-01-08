package br.com.mv.cccopilotpropertie.search.infra;

import br.com.mv.cccopilotpropertie.search.domain.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public class PostgresSearchRepository implements SearchRepository {

    private final JdbcTemplate jdbc;

    public PostgresSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchResult> search(String tenantId,
                                     String knowledgeBase,
                                     float[] vector,
                                     int limit) {

        return jdbc.query("""
            SELECT path, content, 1 - (embedding <=> ?) AS score
            FROM code_embeddings
            WHERE tenant_id = ?
              AND knowledge_base = ?
            ORDER BY embedding <=> ?
            LIMIT ?
        """, ps -> {
            ps.setObject(1, vector);
            ps.setString(2, tenantId);
            ps.setString(3, knowledgeBase);
            ps.setObject(4, vector);
            ps.setInt(5, limit);
        }, (rs, i) -> new SearchResult(
                rs.getString("path"),
                rs.getString("content"),
                rs.getDouble("score"),
                rs.getString("knowledge_base"),
                rs.getString("tenant_id")
        ));

    }
}
