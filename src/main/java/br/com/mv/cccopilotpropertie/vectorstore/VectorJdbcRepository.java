package br.com.mv.cccopilotpropertie.vectorstore;

import br.com.mv.cccopilotpropertie.domain.CodeChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorJdbcRepository implements VectorRepository {


    private final JdbcTemplate jdbc;

    public VectorJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(CodeChunk c) {
        jdbc.update("""
                    INSERT INTO code_embeddings(id, path, content, embedding)
                    VALUES (?, ?, ?, ?)
                """, c.getId(), c.getPath(), c.getContent(), c.getEmbedding());
    }

    @Override
    public List<String> search(float[] vector) {
        return jdbc.queryForList("""
                    SELECT content FROM code_embeddings
                    ORDER BY embedding <-> ?
                    LIMIT 5
                """, String.class, vector);
    }
}

