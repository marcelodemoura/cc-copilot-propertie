package br.com.mv.cccopilotpropertie.vector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class EmbeddingRepository {

    private final JdbcTemplate jdbc;

    public EmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(
            UUID id,
            String tenantId,
            String knowledgeBase,
            String path,
            String content,
            float[] vector
    ) {
        jdbc.update("""
            INSERT INTO code_embeddings (
                id,
                tenant_id,
                knowledge_base,
                path,
                content,
                embedding
            )
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                knowledge_base = EXCLUDED.knowledge_base,
                path = EXCLUDED.path,
                content = EXCLUDED.content,
                embedding = EXCLUDED.embedding
        """,
                id,
                tenantId,
                knowledgeBase,
                path,
                content,
                vector
        );
    }

    public void deleteByPath(String path) {
        jdbc.update(
                "DELETE FROM code_embeddings WHERE path = ?",
                path
        );
    }
}
