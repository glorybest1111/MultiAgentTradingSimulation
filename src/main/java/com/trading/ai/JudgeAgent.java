package com.trading.ai;

import com.trading.agent.MarketSnapshot;
import com.trading.model.Action;
import com.trading.model.Decision;

/**
 * 辩论系统 - 裁判（Judge）：综合多空双方观点给出最终交易决策。
 * 双方置信度差距小于阈值时判为观望（市场分歧不足）。
 */
public class JudgeAgent {

    private static final double DECIDE_THRESHOLD = 0.20;

    public Decision judge(Opinion bull, Opinion bear, MarketSnapshot snapshot) {
        double difference = bull.getConfidence() - bear.getConfidence();
        if (Math.abs(difference) < DECIDE_THRESHOLD) {
            return Decision.hold("JudgeAgent",
                    String.format("debate inconclusive: bull %.2f vs bear %.2f",
                            bull.getConfidence(), bear.getConfidence()));
        }
        Action action = difference > 0 ? Action.BUY : Action.SELL;
        int quantity = (int) Math.round(20 * Math.abs(difference));
        String reason = String.format(
                "debate settled for %s: bull(%.2f) vs bear(%.2f) | bull: %s | bear: %s",
                action,
                bull.getConfidence(), bear.getConfidence(),
                String.join("; ", bull.getReasons()),
                String.join("; ", bear.getReasons()));
        return Decision.trade("JudgeAgent", action, quantity, Math.abs(difference), reason);
    }

}
