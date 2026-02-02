package br.com.mv.cccopilotpropertie.copilot.domain;

public enum QuestionIntent {

    // 🧠 Entendimento do projeto
    UNDERSTAND_PROJECT,

    // 📍 Campo
    LOCATE_FIELD,
    CHECK_FIELD_USAGE,
    REMOVE_FIELD,

    // 💥 Breaking / versionamento
    CHECK_BREAKING,
    CHECK_VERSIONING,

    // 🌐 Contratos
    CHECK_HTTP_CONTRACT,
    CHECK_EXTERNAL_IMPACT,

    // 📦 DTO
    CHECK_DTO_USAGE,
    AUDIT_DTO,

    // 🔗 Endpoints
    LOCATE_ENDPOINTS,

    // 🧠 Fallback
    GENERIC
}
