package com.trading.ai;

import com.trading.agent.MarketSnapshot;
import com.trading.market.PriceHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * 辩论系统 - 看跌方（Bear）：从趋势、情绪、流动性角度收集看跌证据。
 */
public class BearAgent {

    public Opinion formOpinion(MarketSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        double confidence = 0.5;
        PriceHistory history = snapshot.getHistory();

        // 趋势证据
        double momentum = history.momentum(10);
        if (!Double.isNaN(momentum)) {
            if (momentum < -0.003) {
                confidence += 0.15;
                reasons.add(String.format("momentum negative (%.2f%%)", momentum * 100));
            } else if (momentum > 0.003) {
                confidence -= 0.15;
                reasons.add(String.format("momentum positive (+%.2f%%)", momentum * 100));
            }
            double shortSma = history.sma(5);
            if (!Double.isNaN(shortSma)) {
                if (snapshot.getPrice() < shortSma) {
                    confidence += 0.10;
                    reasons.add("price below short-term average");
                } else {
                    confidence -= 0.10;
                    reasons.add("price above short-term average");
                }
            }
        }

        // 情绪证据
        if (snapshot.getMarketMood() < -0.15) {
            confidence += 0.15;
            reasons.add("negative market sentiment");
        } else if (snapshot.getMarketMood() > 0.15) {
            confidence -= 0.15;
            reasons.add("positive market sentiment");
        }

        // 拥挤度证据：连续上涨后的回调风险
        double longMomentum = history.momentum(30);
        if (!Double.isNaN(longMomentum) && longMomentum > 0.02) {
            confidence += 0.10;
            reasons.add("market overheated after long rally");
        }

        if (reasons.isEmpty()) {
            reasons.add("no strong bearish signal");
        }
        return new Opinion("BearAgent", "BEAR", clamp(confidence), reasons);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

}
