package com.trading.ai;

import com.trading.agent.MarketSnapshot;
import com.trading.market.PriceHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * 辩论系统 - 看涨方（Bull）：从趋势、情绪、流动性角度收集看涨证据。
 */
public class BullAgent {

    public Opinion formOpinion(MarketSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        double confidence = 0.5;
        PriceHistory history = snapshot.getHistory();

        // 趋势证据
        double momentum = history.momentum(10);
        if (!Double.isNaN(momentum)) {
            if (momentum > 0.003) {
                confidence += 0.15;
                reasons.add(String.format("momentum positive (+%.2f%%)", momentum * 100));
            } else if (momentum < -0.003) {
                confidence -= 0.15;
                reasons.add(String.format("momentum negative (%.2f%%)", momentum * 100));
            }
            double shortSma = history.sma(5);
            if (!Double.isNaN(shortSma)) {
                if (snapshot.getPrice() > shortSma) {
                    confidence += 0.10;
                    reasons.add("price above short-term average");
                } else {
                    confidence -= 0.10;
                    reasons.add("price below short-term average");
                }
            }
        }

        // 情绪证据
        if (snapshot.getMarketMood() > 0.15) {
            confidence += 0.15;
            reasons.add("positive market sentiment");
        } else if (snapshot.getMarketMood() < -0.15) {
            confidence -= 0.15;
            reasons.add("negative market sentiment");
        }

        // 流动性证据
        if (snapshot.hasBid() && snapshot.hasAsk() && snapshot.getSpread() / snapshot.getPrice() < 0.002) {
            confidence += 0.05;
            reasons.add("tight spread signals liquidity");
        }

        if (reasons.isEmpty()) {
            reasons.add("no strong bullish signal");
        }
        return new Opinion("BullAgent", "BULL", clamp(confidence), reasons);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

}
