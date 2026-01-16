package br.com.mv.cccopilotpropertie.domain;

import java.util.UUID;
public class CodeChunk {

    private UUID id;
    private String tenantId;
    private String knowledgeBase;
    private String path;
    private String content;
    private float[] embedding;

    public CodeChunk(
            UUID id,
            String tenantId,
            String knowledgeBase,
            String path,
            String content,
            float[] embedding
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.knowledgeBase = knowledgeBase;
        this.path = path;
        this.content = content;
        this.embedding = embedding;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }
}

