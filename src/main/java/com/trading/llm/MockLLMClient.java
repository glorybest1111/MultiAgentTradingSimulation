package com.trading.llm;

import java.util.Locale;

/**
 * 本地规则版 LLM 客户端：把大模型调用「降级」为确定性规则，用于离线演示与回归测试。
 *
 * <p>它解析 userPrompt 中的结构化参数（由 {@code com.trading.llm.PromptBuilder} 生成），
 * 输出与真实大模型完全一致的输出协议：
 * <pre>
 * ACTION=BUY|SELL|HOLD QTY=n CONFIDENCE=0.xx REASON=一句话理由
 * </pre>
 *
 * <p>因为是纯本地实现，同一份输入永远得到同一份输出，保证实验可复现。
 */
public class MockLLMClient implements LLMClient {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        double mood = readValue(userPrompt, "MOOD");
        double trend = readValue(userPrompt, "TREND");       // 动量，+为上涨
        double deviation = readValue(userPrompt, "DEVIATION"); // 相对均线偏离
        int riskBudget = (int) Math.round(readValue(userPrompt, "MAX_QTY"));

        boolean conservative = systemPrompt != null
                && systemPrompt.toUpperCase(Locale.ROOT).contains("CONSERVATIVE");
        double buyGate = conservative ? 0.35 : 0.05;
        double sellGate = conservative ? -0.45 : -0.10;

        double score = mood * 0.5 + trend * 25 + (-deviation) * 10;
        int quantity = 1 + (int) Math.round(Math.abs(score) * (riskBudget > 0 ? riskBudget : 10));
        quantity = Math.max(1, Math.min(quantity, Math.max(1, riskBudget)));

        if (score > buyGate) {
            return String.format(Locale.ROOT,
                    "ACTION=BUY QTY=%d CONFIDENCE=%.2f REASON=mock scoring %.2f bullish (%s)",
                    quantity, Math.min(0.95, 0.5 + score * 0.3), score,
                    conservative ? "conservative mode, reduced size" : "aggressive mode");
        }
        if (score < sellGate) {
            return String.format(Locale.ROOT,
                    "ACTION=SELL QTY=%d CONFIDENCE=%.2f REASON=mock scoring %.2f bearish (%s)",
                    quantity, Math.min(0.95, 0.5 - score * 0.3), score,
                    conservative ? "protect capital first" : "momentum mode");
        }
        return String.format(Locale.ROOT,
                "ACTION=HOLD QTY=0 CONFIDENCE=0.10 REASON=mock scoring %.2f within neutral band", score);
    }

    /** 从 "KEY=1.23" 形式的结构化提示中读取数值，读不到返回 0 */
    private static double readValue(String text, String key) {
        if (text == null) {
            return 0;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        int index = upper.indexOf(key.toUpperCase(Locale.ROOT) + "=");
        if (index < 0) {
            return 0;
        }
        int start = index + key.length() + 1;
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        try {
            return Double.parseDouble(text.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
