-- Adiciona coluna de hash de arquivo para indexação incremental
ALTER TABLE code_embeddings ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_code_embeddings_path
    ON code_embeddings (tenant_id, knowledge_base, path);
