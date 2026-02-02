package br.com.mv.cccopilotpropertie.copilot.intent;

public enum CopilotIntent {

    FIELD_REMOVAL,          // posso remover o campo X?
    FIELD_USAGE,            // onde / como o campo é usado
    BREAKING_CHANGE,        // isso é breaking?
    HTTP_CONTRACT_IMPACT,   // isso quebra alguma API?
    EXTERNAL_IMPACT,        // impacta outro sistema?
    ENDPOINT_USAGE,         // quais endpoints usam?
    DTO_AUDIT,              // auditoria / risco
    PROJECT_UNDERSTANDING,  // o que esse projeto faz?
    GENERIC_QUESTION        // fallback RAG
}
