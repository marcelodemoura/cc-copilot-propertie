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

    @Override
    public Optional<SearchResult> findByClassName(
            String tenantId,
            String knowledgeBase,
            String className
    ) {
        return jdbc.query("""
                            SELECT path, content, 1.0 AS score
                            FROM code_embeddings
                            WHERE tenant_id = ?
                              AND knowledge_base = ?
                              AND path ILIKE ?
                            LIMIT 1
                        """,
                ps -> {
                    ps.setString(1, tenantId);
                    ps.setString(2, knowledgeBase);
                    ps.setString(3, "%/" + className + ".java");
                },
                rs -> rs.next()
                        ? Optional.of(new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ))
                        : Optional.empty()
        );
    }

    @Override
    public List<SearchResult> findUsagesByClassName(
            String tenantId,
            String knowledgeBase,
            String className
    ) {
        return jdbc.query("""
                            SELECT DISTINCT ON (path)
                                                        path,
                                                        content,
                                                        1.0 AS score
                                                    FROM code_embeddings
                                                    WHERE tenant_id = ?
                                                      AND knowledge_base = ?
                                                      AND content ILIKE ?
                                                      AND path NOT ILIKE ?
                                                    ORDER BY path
                                                    LIMIT 50
                        
                        """,
                (rs, i) -> new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ),
                tenantId,
                knowledgeBase,
                "%" + className + "%",
                "%/" + className + ".java"
        );
    }

    @Override
    public Optional<SearchResult> findDtoDefinitionGlobal(
            String tenantId,
            String dtoName
    ) {
        return jdbc.query("""
                            SELECT path, content, 1.0 AS score
                            FROM code_embeddings
                            WHERE tenant_id = ?
                              AND (path ILIKE ? OR content ILIKE ?)
                            LIMIT 1
                        """,
                ps -> {
                    ps.setString(1, tenantId);
                    ps.setString(2, "%/" + dtoName + ".java");
                    ps.setString(3, "%class " + dtoName + "%");
                },
                rs -> rs.next()
                        ? Optional.of(new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ))
                        : Optional.empty()
        );
    }

    @Override
    public List<SearchResult> findUsagesInOtherKnowledgeBases(
            String tenantId,
            String excludeKnowledgeBase,
            String dtoName
    ) {
        return jdbc.query("""
                            SELECT path, content, 1.0 AS score
                            FROM code_embeddings
                            WHERE tenant_id = ?
                              AND knowledge_base <> ?
                              AND content ILIKE ?
                              AND path NOT ILIKE ?
                            ORDER BY path
                            LIMIT 50
                        """,
                (rs, i) -> new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ),
                tenantId,
                excludeKnowledgeBase,
                "%" + dtoName + "%",
                "%/" + dtoName + ".java"
        );
    }
}
