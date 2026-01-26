package br.com.mv.cccopilotpropertie.copilot.alert;

public class BuildFailException extends RuntimeException {
    public BuildFailException(String message) {
        super(message);
    }
}
