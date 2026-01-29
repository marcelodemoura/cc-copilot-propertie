package br.com.mv.cccopilotpropertie.copilot.breaking;

public record BreakingAnalysisResult(

        BreakingType breakingType,
        String reason,
        boolean requiresVersioning

){}