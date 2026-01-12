package br.com.mv.cccopilotpropertie.index.infra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class CodeEmbeddingRepository {

    private final JdbcTemplate jdbc;

    public CodeEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

//    public void save(
//            UUID id,
//            String tenantId,
//            String knowledgeBase,
//            String path,
//            String content,
//            float[] embedding
//    ) {
//
//        jdbc.update("""
//            INSERT INTO code_embeddings (
//                id,
//                tenant_id,
//                knowledge_base,
//                path,
//                content,
//                embedding
//            )
//            VALUES (?, ?, ?, ?, ?, ?)
//        """, ps -> {
//            ps.setObject(1, id);
//            ps.setString(2, tenantId);
//            ps.setString(3, knowledgeBase);
//            ps.setString(4, path);
//            ps.setString(5, content);
//            ps.setObject(6, embedding); // pgvector
//        });

    public void save(
            String tenantId,
            String knowledgeBase,
            String path,
            String content,
            float[] embedding
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
                """, ps -> {
            ps.setObject(1, UUID.randomUUID()); // 🔴 TEM que ser aqui
            ps.setString(2, tenantId);
            ps.setString(3, knowledgeBase);
            ps.setString(4, path);
            ps.setString(5, content);
            ps.setObject(6, embedding);
        });
    }

}

