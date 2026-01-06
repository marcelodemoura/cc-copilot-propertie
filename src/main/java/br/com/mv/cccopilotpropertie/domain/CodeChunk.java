package br.com.mv.cccopilotpropertie.domain;

import java.util.UUID;

public class CodeChunk {


    private UUID id;
    private String path;
    private String content;
    private float[] embedding;

    public CodeChunk(UUID id, String path, String content, float[] embedding) {
        this.id = id;
        this.path = path;
        this.content = content;
        this.embedding = embedding;
    }

    public UUID getId() {
        return id;
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
