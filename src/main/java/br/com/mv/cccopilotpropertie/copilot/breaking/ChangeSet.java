package br.com.mv.cccopilotpropertie.copilot.breaking;

public record ChangeSet(
        ChangeTarget target,
        ChangeType type,
        String elementName,
        String dtoName,          // 🔥 NOVO
        Object beforeState,
        Object afterState
) {
}