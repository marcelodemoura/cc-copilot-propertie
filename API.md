# API Reference — CC Copilot Properties

Base URL: `http://localhost:8080`  
Autenticação: todas as chamadas exigem o header `X-API-Key: local-dev-key` (exceto `/swagger-ui` e `/v3/api-docs`).

---

## Fluxo recomendado

```
1. POST /projects          → cadastra o projeto
2. POST /projects/{id}/index  → indexa o código
3. POST /projects/{id}/ask    → faz perguntas ao agente
```

---

## 1. Projetos

### `POST /projects` — Cadastra um projeto

Registra um projeto local. O `rootPath` precisa estar dentro de `INDEXER_BASE_PATH` (padrão: diretório onde a app subiu).

**Request**
```json
{
  "tenantId": "default",
  "name": "painel-ws",
  "rootPath": "/home/marcelo/IdeaProjects/painel-ws"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tenantId` | string | sim | Identificador do tenant/organização |
| `name` | string | sim | Nome do projeto (único por tenant) |
| `rootPath` | string | sim | Caminho absoluto do projeto no sistema de arquivos |

**Response `200`**
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "default",
  "name": "painel-ws",
  "rootPath": "/home/marcelo/IdeaProjects/painel-ws",
  "createdAt": "2026-08-21T13:00:00Z"
}
```

> Guarde o `id` — ele é usado em todos os outros endpoints de projeto.

---

### `POST /projects/{id}/index` — Indexa o projeto

Lê todos os arquivos `.java`, `.kt`, `.groovy`, `.yaml`, `.sql` e `.md` dentro do `rootPath`, gera embeddings e armazena no pgvector. Reindexações são destrutivas (apaga e recria).

**Path param**

| Param | Descrição |
|---|---|
| `id` | UUID do projeto retornado no cadastro |

**Sem body.**

**Response `200`**
```json
{
  "jobId": "a1b2c3d4-...",
  "filesIndexed": 42,
  "chunksIndexed": 318
}
```

> Reindexe sempre que o código do projeto mudar.

---

### `POST /projects/{id}/ask` — Pergunta ao agente

O agente usa tool calling para buscar código, analisar DTOs, detectar breaking changes e auditar contratos. Ele raciocina em loop (até 8 iterações) antes de responder.

**Path param**

| Param | Descrição |
|---|---|
| `id` | UUID do projeto |

**Request**
```json
{
  "question": "posso remover o campo cnpj do ClienteDTO?",
  "sessionId": "dev-1"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `question` | string | sim | Pergunta em linguagem natural |
| `sessionId` | string | não | ID da sessão para manter contexto entre perguntas |

**Response `200`**
```json
{
  "answer": "O campo `cnpj` é utilizado em 3 arquivos...",
  "sources": [
    { "path": "/src/main/java/ClienteController.java", "score": 0.94 },
    { "path": "/src/main/java/ClienteService.java", "score": 0.91 }
  ],
  "confidence": 0.92,
  "structured": null,
  "alert": null,
  "suggestedPatch": null,
  "plan": null
}
```

| Campo | Descrição |
|---|---|
| `answer` | Resposta textual do agente |
| `sources` | Arquivos usados como base, com score de relevância |
| `confidence` | Confiança média da resposta (0–1) |
| `structured` | Resultado estruturado quando a pergunta é uma auditoria de DTO |
| `alert` | Alerta de risco (`CRITICAL`, `WARNING`, `INFO`) quando detectado |
| `suggestedPatch` | Diff sugerido quando aplicável |
| `plan` | Plano de alterações quando solicitado |

**Exemplos de perguntas**
```
"O que esse projeto faz?"
"Quais endpoints existem?"
"Posso remover o campo cpf do PessoaDTO?"
"Isso é uma breaking change?"
"Esse DTO é usado em outros sistemas?"
"Faz uma auditoria do ClienteDTO"
"Onde devo alterar para adicionar um novo campo de telefone?"
```

**Usando sessionId para perguntas sequenciais**
```bash
# Pergunta 1
{ "sessionId": "s1", "question": "posso remover o campo email do UsuarioDTO?" }

# Pergunta 2 — o agente lembra do contexto anterior
{ "sessionId": "s1", "question": "isso quebra alguma API?" }

# Pergunta 3
{ "sessionId": "s1", "question": "e impacta outro sistema?" }
```

---

## 2. Histórico

### `GET /history` — Lista histórico de interações

Retorna paginado todas as perguntas e respostas registradas.

**Query params**

| Param | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tenantId` | string | sim | Filtro por tenant |
| `knowledgeBase` | string | não | Filtro por base de conhecimento (= `id` do projeto) |
| `page` | integer | não | Página (padrão: 0) |
| `size` | integer | não | Itens por página (padrão: 20) |

**Exemplo**
```
GET /history?tenantId=default&knowledgeBase=3fa85f64-...&page=0&size=10
```

**Response `200`**
```json
{
  "content": [
    {
      "tenantId": "default",
      "knowledgeBase": "3fa85f64-...",
      "question": "posso remover o campo cnpj?",
      "answer": "O campo cnpj é utilizado em...",
      "confidence": 0.92,
      "createdAt": "2026-08-21T13:05:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 5,
  "number": 0
}
```

---

## 3. Auditoria

### `GET /copilot/audit/dtos` — DTOs mais auditados

Retorna os DTOs com mais execuções de auditoria, ordenados por frequência.

**Sem params.**

**Response `200`**
```json
[
  {
    "key": "ClienteDTO",
    "total": 12,
    "critical": 2,
    "highRisk": 5
  }
]
```

| Campo | Descrição |
|---|---|
| `key` | Nome do DTO |
| `total` | Total de auditorias executadas |
| `critical` | Quantidade com alerta CRITICAL |
| `highRisk` | Quantidade com risco ALTO |

---

### `GET /copilot/audit/projects` — Auditoria por projeto

Mesma estrutura acima, agrupada por `knowledgeBase` em vez de DTO.

---

## 4. Knowledge Bases

### `POST /tenants/{tenantId}/knowledge-bases` — Cria uma base de conhecimento

Cria uma base de conhecimento manualmente (usado pelo fluxo legado).

**Path param**

| Param | Descrição |
|---|---|
| `tenantId` | Identificador do tenant |

**Query param**

| Param | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `name` | string | sim | Nome da base de conhecimento |

**Exemplo**
```
POST /tenants/default/knowledge-bases?name=painel-ws
```

**Response `200`**
```json
{
  "id": "...",
  "tenantId": "default",
  "name": "painel-ws"
}
```

---

## 5. Endpoints legados

> Funcionam sem cadastrar projeto. Use apenas para testes rápidos ou integração com sistemas antigos.

### `POST /index` — Indexa um diretório diretamente

**Query params**

| Param | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `path` | string | sim | Caminho absoluto do diretório a indexar |
| `knowledgeBase` | string | sim | Nome da base de conhecimento |

**Exemplo**
```
POST /index?path=/home/marcelo/IdeaProjects/painel-ws&knowledgeBase=painel-ws
```

**Response `200`**
```json
{
  "jobId": "...",
  "filesIndexed": 38,
  "chunksIndexed": 290
}
```

---

### `POST /copilot/ask` — Pergunta sem projeto

**Request**
```json
{
  "tenantId": "default",
  "knowledgeBase": "painel-ws",
  "question": "quais endpoints existem?",
  "sessionId": "dev-1"
}
```

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tenantId` | string | sim | Identificador do tenant |
| `knowledgeBase` | string | sim | Nome da base de conhecimento |
| `question` | string | sim | Pergunta em linguagem natural |
| `sessionId` | string | não | ID da sessão para contexto |

**Response** — mesmo formato do `POST /projects/{id}/ask`.

---

## Configuração necessária para indexar projetos externos

Por padrão o `INDEXER_BASE_PATH` é `.` (diretório onde a app subiu). Para indexar projetos em outros diretórios, suba a aplicação com:

```bash
INDEXER_BASE_PATH=/home/marcelo/IdeaProjects ./gradlew bootRun
```

Ou defina no `.env`:
```
INDEXER_BASE_PATH=/home/marcelo/IdeaProjects
```
