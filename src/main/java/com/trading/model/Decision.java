package com.trading.model;

import java.time.LocalDateTime;

/**
 * AI 决策记录：每个 Agent 每次决策的可解释记录（Explainable AI 的核心对象）。
 */
public class Decision {

    private final String agentName;
    private final Action action;
    private final int quantity;
    private final double confidence;   // 0..1，决策置信度
    private final String reason;       // 决策理由（人类可读）
    private final LocalDateTime timestamp;

    /** 决策时的市场价格：由仿真系统在记录日志时回填，用于胜率/画像计算 */
    private double decisionPrice = Double.NaN;

    public Decision(String agentName, Action action, int quantity, double confidence, String reason) {
        this.agentName = agentName;
        this.action = action;
        this.quantity = quantity;
        this.confidence = confidence;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    /** 不交易 */
    public static Decision hold(String agentName, String reason) {
        return new Decision(agentName, Action.HOLD, 0, 0, reason);
    }

    /** 交易决策 */
    public static Decision trade(String agentName, Action action, int quantity, double confidence, String reason) {
        return new Decision(agentName, action, quantity, confidence, reason);
    }

    public String getAgentName() {
        return agentName;
    }

    public Action getAction() {
        return action;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /** 回填决策时的市价（由 MarketSimulation 调用） */
    public void setDecisionPrice(double price) {
        this.decisionPrice = price;
    }

    public double getDecisionPrice() {
        return decisionPrice;
    }

    @Override
    public String toString() {
        return "Decision{agent=" + agentName +
                ", action=" + action +
                ", qty=" + quantity +
                ", confidence=" + String.format("%.2f", confidence) +
                ", reason='" + reason + "'}";
    }

}
