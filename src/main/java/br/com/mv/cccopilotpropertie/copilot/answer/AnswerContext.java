package br.com.mv.cccopilotpropertie.copilot.answer;

import br.com.mv.cccopilotpropertie.copilot.breaking.BreakingType;
import br.com.mv.cccopilotpropertie.copilot.breaking.ChangeType;
import br.com.mv.cccopilotpropertie.copilot.domain.CopilotAnswer;
import br.com.mv.cccopilotpropertie.copilot.intent.CopilotIntent;

import java.util.List;

public record AnswerContext(
        CopilotIntent intent,
        String dto,
        String field,
        ChangeType changeType,
        BreakingType breakingType,
        String reason,
        boolean requiresVersioning,
        List<CopilotAnswer.Source> sources
) {}
