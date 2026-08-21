CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS code_embeddings (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    knowledge_base VARCHAR(255) NOT NULL,
    path TEXT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL
);

ALTER TABLE code_embeddings
    DROP CONSTRAINT IF EXISTS code_embeddings_tenant_id_knowledge_base_path_content_key;

CREATE INDEX IF NOT EXISTS code_embeddings_scope_idx
    ON code_embeddings (tenant_id, knowledge_base);

CREATE INDEX IF NOT EXISTS code_embeddings_embedding_idx
    ON code_embeddings USING hnsw (embedding vector_cosine_ops);
