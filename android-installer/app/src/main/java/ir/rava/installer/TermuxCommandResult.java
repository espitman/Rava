package ir.rava.installer;

final class TermuxCommandResult {
    final int executionId;
    final int exitCode;
    final int internalError;
    final String stdout;
    final String stderr;
    final String errorMessage;

    TermuxCommandResult(int executionId, int exitCode, int internalError,
            String stdout, String stderr, String errorMessage) {
        this.executionId = executionId;
        this.exitCode = exitCode;
        this.internalError = internalError;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    boolean succeeded() {
        return exitCode == 0 && internalError == -1;
    }
}
