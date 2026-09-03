# CC Copilot Properties

Copiloto técnico autônomo para análise de projetos de software. Você indexa um projeto local e faz perguntas em linguagem natural — o agente decide sozinho quais ferramentas usar, busca o código relevante, raciocina sobre o resultado e responde com fontes.

## Como funciona

Diferente de um RAG simples, a API usa um **loop agentic com tool calling** (OpenAI function calling). A cada pergunta:

1. O LLM recebe a pergunta e decide quais ferramentas chamar
2. A API executa as ferramentas (busca vetorial, análise de breaking change, auditoria de DTO, etc.)
3. O resultado é devolvido ao LLM, que pode chamar mais ferramentas ou formular a resposta final
4. O ciclo repete até o LLM ter informação suficiente (máximo 8 iterações)

Não há classificação manual de intenção — o modelo raciocina livremente.

## Ferramentas disponíveis para o agente

| Tool | O que faz |
|---|---|
| `search_code` | Busca semântica em código, config e documentação |
| `find_dto_definition` | Localiza a definição de um DTO pelo nome |
| `find_dto_usages` | Lista todos os arquivos que referenciam um DTO |
| `find_endpoints_using_dto` | Lista controllers REST que usam um DTO |
| `find_external_usages` | Verifica se um DTO é usado em outros projetos |
| `analyze_breaking_change` | Analisa se remover/alterar um campo é breaking change |
| `audit_dto` | Auditoria completa: risco, validações, contratos, recomendações |

## Subir localmente

1. Suba o PostgreSQL com pgvector:
```bash
docker compose up -d
```

2. Copie as variáveis de ambiente:
```bash
cp .env.example .env
```

3. Execute com perfil mock (sem chamadas externas, ideal para desenvolvimento):
```bash
./gradlew bootRun
```

Para usar o agente de verdade com OpenAI:
```bash
SPRING_PROFILES_ACTIVE=openai OPENAI_API_KEY=sk-... ./gradlew bootRun
```

Toda chamada precisa do header `X-API-Key` com o valor configurado em `API_KEY` (padrão: `local-dev-key`).

## Fluxo de uso

### 1. Cadastre o projeto

O `rootPath` precisa estar dentro de `INDEXER_BASE_PATH` (padrão: diretório atual).

```bash
curl -X POST http://localhost:8080/projects \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-dev-key' \
  -d '{"tenantId":"default","name":"meu-projeto","rootPath":"/caminho/do/projeto"}'
```

Resposta:
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "tenantId": "default",
  "name": "meu-projeto",
  "rootPath": "/caminho/do/projeto"
}
```

### 2. Indexe o projeto

Use o `id` retornado. A indexação lê todos os arquivos `.java`, `.kt`, `.groovy`, `.yaml`, `.sql` e `.md` dentro do `rootPath`, gera embeddings e armazena no pgvector.

```bash
curl -X POST http://localhost:8080/projects/3fa85f64-5717-4562-b3fc-2c963f66afa6/index \
  -H 'X-API-Key: local-dev-key'
```

Resposta:
```json
{
  "jobId": "...",
  "filesIndexed": 42,
  "chunksIndexed": 318
}
```

### 3. Faça perguntas

Use o mesmo `sessionId` para manter contexto entre perguntas relacionadas. O agente usa o histórico da sessão para entender referências como "isso", "esse campo", "esse DTO".

```bash
curl -X POST http://localhost:8080/projects/3fa85f64-5717-4562-b3fc-2c963f66afa6/ask \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-dev-key' \
  -d '{"sessionId":"dev-1","question":"posso remover o campo cnpj do ClienteDTO?"}'
```

Resposta:
```json
{
  "answer": "O campo `cnpj` do ClienteDTO é utilizado em 3 arquivos...",
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

## Exemplos de perguntas

O agente entende linguagem natural. Alguns exemplos:

```
"O que esse projeto faz?"
"Quais endpoints existem?"
"Posso remover o campo cpf do PessoaDTO?"
"Isso é uma breaking change?"
"Esse DTO é usado em outros sistemas?"
"Faz uma auditoria do ClienteDTO"
"Onde devo alterar para adicionar um novo campo de telefone?"
"Quais DTOs têm risco alto?"
```

Perguntas sequenciais na mesma sessão funcionam com referências implícitas:

```bash
# Pergunta 1
{"sessionId":"s1","question":"posso remover o campo email do UsuarioDTO?"}

# Pergunta 2 — o agente sabe que "isso" é o campo email do UsuarioDTO
{"sessionId":"s1","question":"isso quebra alguma API?"}

# Pergunta 3
{"sessionId":"s1","question":"e impacta outro sistema?"}
```

## Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/projects` | Cadastra um projeto |
| `POST` | `/projects/{id}/index` | Indexa o projeto |
| `POST` | `/projects/{id}/ask` | Pergunta ao agente sobre o projeto |
| `POST` | `/index?path=&knowledgeBase=` | Indexação manual (legado) |
| `POST` | `/copilot/ask` | Pergunta sem projeto (legado) |
| `POST` | `/tenants/{tenantId}/knowledge-bases?name=` | Cria base de conhecimento |
| `GET` | `/history?tenantId=&knowledgeBase=` | Histórico paginado de interações |
| `GET` | `/copilot/audit/dtos` | Métricas de auditoria de DTOs |

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `mock` | `mock` (sem OpenAI) ou `openai` |
| `OPENAI_API_KEY` | — | Chave da API OpenAI (obrigatório no perfil `openai`) |
| `OPENAI_MODEL` | `gpt-5-mini` | Modelo a usar |
| `API_KEY` | `local-dev-key` | Chave do header `X-API-Key` |
| `DB_URL` | `jdbc:postgresql://localhost:5434/copilot` | URL do banco |
| `DB_USERNAME` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |
| `INDEXER_BASE_PATH` | `.` | Diretório raiz permitido para indexação |
