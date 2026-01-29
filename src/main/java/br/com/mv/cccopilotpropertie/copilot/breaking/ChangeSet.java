package br.com.mv.cccopilotpropertie.copilot.breaking;

public record ChangeSet(
        ChangeTarget target,
        ChangeType type,
        String elementName,
        Object beforeState,
        Object afterState
) {
}