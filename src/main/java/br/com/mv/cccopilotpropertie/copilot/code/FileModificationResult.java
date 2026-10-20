package br.com.mv.cccopilotpropertie.copilot.code;

public record FileModificationResult(
        boolean success,
        String path,
        String status,
        String diff,
        String backupPath,
        String message
) {

    public static FileModificationResult success(String path, String diff, String backupPath, String message) {
        return new FileModificationResult(true, path, "MODIFIED", diff, backupPath, message);
    }

    public static FileModificationResult created(String path, String diff, String message) {
        return new FileModificationResult(true, path, "CREATED", diff, null, message);
    }

    public static FileModificationResult restored(String path, String message) {
        return new FileModificationResult(true, path, "RESTORED", null, null, message);
    }

    public static FileModificationResult failure(String path, String message) {
        return new FileModificationResult(false, path, "FAILED", null, null, message);
    }
}
