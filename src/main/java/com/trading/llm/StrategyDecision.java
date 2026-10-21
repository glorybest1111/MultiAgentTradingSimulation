package com.trading.llm;

import com.trading.model.Action;

import java.util.Locale;

/**
 * 大模型返回的交易决策：把纯文本协议解析成强类型对象。
 *
 * <p>协议：{@code ACTION=BUY|SELL|HOLD QTY=n CONFIDENCE=0.xx REASON=...}
 * 任何无法解析的回复都会被安全地降级为 HOLD，绝不会因为模型输出异常而误下单。
 */
public class StrategyDecision {

    private final Action action;
    private final int quantity;
    private final double confidence;
    private final String reason;

    public StrategyDecision(Action action, int quantity, double confidence, String reason) {
        this.action = action;
        this.quantity = Math.max(0, quantity);
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.reason = reason;
    }

    public static StrategyDecision hold(String reason) {
        return new StrategyDecision(Action.HOLD, 0, 0, reason);
    }

    /** 解析模型输出，解析失败时返回 HOLD */
    public static StrategyDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return hold("empty model output");
        }
        String text = raw.trim();
        Action action = Action.HOLD;
        int qty = 0;
        double confidence = 0;
        String reason = text;

        String upper = text.toUpperCase(Locale.ROOT);
        int actionIndex = upper.indexOf("ACTION=");
        if (actionIndex >= 0) {
            String value = nextToken(text, actionIndex + "ACTION=".length());
            try {
                action = Action.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return hold("unparsable ACTION: " + value);
            }
        } else {
            return hold("missing ACTION field");
        }

        int qtyIndex = upper.indexOf("QTY=");
        if (qtyIndex >= 0) {
            try {
                qty = (int) Double.parseDouble(nextToken(text, qtyIndex + 4));
            } catch (NumberFormatException ignored) {
                qty = 0;
            }
        }

        int confidenceIndex = upper.indexOf("CONFIDENCE=");
        if (confidenceIndex >= 0) {
            try {
                confidence = Double.parseDouble(nextToken(text, confidenceIndex + "CONFIDENCE=".length()));
            } catch (NumberFormatException ignored) {
                confidence = 0;
            }
        }

        int reasonIndex = upper.indexOf("REASON=");
        if (reasonIndex >= 0) {
            reason = text.substring(reasonIndex + "REASON=".length()).trim();
        }
        if (action != Action.HOLD && qty <= 0) {
            return hold("action " + action + " has no positive quantity");
        }
        return new StrategyDecision(action, qty, confidence, reason.isEmpty() ? "no reason given" : reason);
    }

    private static String nextToken(String text, int start) {
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end).trim();
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

    @Override
    public String toString() {
        return String.format("StrategyDecision{%s qty=%d conf=%.2f reason=%s}", action, quantity, confidence, reason);
    }
}
