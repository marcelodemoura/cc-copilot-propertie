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
                        SELECT path, content, 1 - (embedding <=> CAST(? AS vector)) AS score
                        FROM code_embeddings
                        WHERE tenant_id = ?
                          AND knowledge_base = ?
                        ORDER BY embedding <=> CAST(? AS vector)
                        LIMIT ?
                        """,
                (rs, i) -> new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        rs.getDouble("score")
                ),
                vectorLiteral(vector),
                tenantId,
                knowledgeBase,
                vectorLiteral(vector),
                limit
        );
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
                rs -> rs.next()
                        ? Optional.of(new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        1.0
                ))
                        : Optional.empty(),
                tenantId,
                knowledgeBase,
                "%/" + className + ".java"
        );
    }

    @Override
    public List<SearchResult> findUsagesByClassName(
            String tenantId,
            String knowledgeBase,
            String className
    ) {
        return jdbc.query("""
                        SELECT DISTINCT path, content, 1.0 AS score
                        FROM code_embeddings
                        WHERE tenant_id = ?
                          AND knowledge_base = ?
                          AND content ILIKE ?
                        """,
                (rs, i) -> new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        1.0
                ),
                tenantId,
                knowledgeBase,
                "%" + className + "%"
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
                        WHERE tenant_id = ? AND path ILIKE ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? Optional.of(new SearchResult(
                        rs.getString("path"), rs.getString("content"), 1.0)) : Optional.empty(),
                tenantId, "%/" + dtoName + ".java");
    }

    @Override
    public List<SearchResult> findUsagesInOtherKnowledgeBases(
            String tenantId,
            String excludeKb,
            String dtoName
    ) {
        return jdbc.query("""
                        SELECT DISTINCT path, content, 1.0 AS score
                        FROM code_embeddings
                        WHERE tenant_id = ? AND knowledge_base <> ? AND content ILIKE ?
                        """,
                (rs, i) -> new SearchResult(rs.getString("path"), rs.getString("content"), 1.0),
                tenantId, excludeKb, "%" + dtoName + "%");
    }

    @Override
    public List<SearchResult> findEndpointsUsingDto(
            String tenantId,
            String knowledgeBase,
            String dtoName
    ) {
        return jdbc.query("""
                        SELECT path, content, 1.0 AS score
                        FROM code_embeddings
                        WHERE tenant_id = ?
                          AND knowledge_base = ?
                          AND (
                                content ILIKE '%@RestController%'
                             OR content ILIKE '%@Controller%'
                          )
                          AND content ILIKE ?
                        """,
                (rs, i) -> new SearchResult(
                        rs.getString("path"),
                        rs.getString("content"),
                        1.0
                ),
                tenantId,
                knowledgeBase,
                "%" + dtoName + "%"
        );
    }

    @Override
    public List<SearchResult> findControllersUsingDto(
            String tenantId,
            String knowledgeBase,
            String dtoName
    ) {
        return findEndpointsUsingDto(tenantId, knowledgeBase, dtoName);
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) value.append(',');
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }
}
