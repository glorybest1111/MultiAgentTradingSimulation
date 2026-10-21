package com.trading.risk;

/**
 * 风控校验结果：通过或拒绝（附带可读原因，用于成交/拒单日志的可解释性）。
 */
public class ValidationResult {

    private static final ValidationResult ACCEPTED = new ValidationResult(true, "ok");

    private final boolean accepted;
    private final String reason;

    private ValidationResult(boolean accepted, String reason) {
        this.accepted = accepted;
        this.reason = reason;
    }

    public static ValidationResult accept() {
        return ACCEPTED;
    }

    public static ValidationResult reject(String reason) {
        return new ValidationResult(false, reason);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getReason() {
        return reason;
    }
}
