-- Migração inicial Flyway: V1__initial_schema.sql
-- Habilita extensão vetorial pgvector se disponível
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Projetos cadastrados
CREATE TABLE IF NOT EXISTS assistant_project (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    root_path TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_project_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_project_tenant ON assistant_project (tenant_id);

-- 2. Knowledge Bases
CREATE TABLE IF NOT EXISTS knowledge_base (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_kb_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_kb_tenant ON knowledge_base (tenant_id);

-- 3. Histórico de interações
CREATE TABLE IF NOT EXISTS copilot_interaction (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    knowledge_base VARCHAR(255) NOT NULL,
    session_id VARCHAR(255),
    question TEXT,
    answer TEXT,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_interaction_scope
    ON copilot_interaction (tenant_id, knowledge_base, session_id, created_at DESC);

-- 4. Auditoria de DTOs e Decisões
CREATE TABLE IF NOT EXISTS copilot_audit_execution (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    knowledge_base VARCHAR(255) NOT NULL,
    dto_name VARCHAR(255) NOT NULL,
    risk_level VARCHAR(50),
    contract_dto BOOLEAN NOT NULL DEFAULT FALSE,
    explicit_validation BOOLEAN NOT NULL DEFAULT FALSE,
    usage_count INTEGER NOT NULL DEFAULT 0,
    alert_level VARCHAR(50),
    audit_type VARCHAR(50),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_kb
    ON copilot_audit_execution (tenant_id, knowledge_base);

CREATE INDEX IF NOT EXISTS idx_audit_dto
    ON copilot_audit_execution (tenant_id, dto_name);

-- 5. Embeddings vetoriais do código
CREATE TABLE IF NOT EXISTS code_embeddings (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    knowledge_base VARCHAR(255) NOT NULL,
    path TEXT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL
);

CREATE INDEX IF NOT EXISTS code_embeddings_scope_idx
    ON code_embeddings (tenant_id, knowledge_base);

CREATE INDEX IF NOT EXISTS code_embeddings_embedding_idx
    ON code_embeddings USING hnsw (embedding vector_cosine_ops);
