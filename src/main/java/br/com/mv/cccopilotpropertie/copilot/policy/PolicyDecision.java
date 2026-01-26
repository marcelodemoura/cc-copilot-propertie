package br.com.mv.cccopilotpropertie.copilot.policy;

public record PolicyDecision(
        boolean allowed,
        String reason
) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, null);
    }

    public static PolicyDecision block(String reason) {
        return new PolicyDecision(false, reason);
    }
}
