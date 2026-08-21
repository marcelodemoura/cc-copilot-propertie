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
            VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
        """,
                id,
                tenantId,
                knowledgeBase,
                path,
                content,
                toVectorLiteral(vector)
        );
    }

    public void deleteByPath(String path) {
        jdbc.update(
                "DELETE FROM code_embeddings WHERE path = ?",
                path
        );
    }

    public void deleteByKnowledgeBase(String tenantId, String knowledgeBase) {
        jdbc.update("DELETE FROM code_embeddings WHERE tenant_id = ? AND knowledge_base = ?", tenantId, knowledgeBase);
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) value.append(',');
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }
}
