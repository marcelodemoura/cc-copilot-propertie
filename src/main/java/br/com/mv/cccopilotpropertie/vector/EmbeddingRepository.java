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

    public void save(UUID id, String path, String content, float[] vector) {
        jdbc.update("""
                    INSERT INTO code_embeddings(id, path, content, embedding)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        path = EXCLUDED.path,
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding
                """, id, path, content, vector);
    }

    public void deleteByPath(String path) {
        jdbc.update("DELETE FROM code_embeddings WHERE path = ?", path);
    }
}
