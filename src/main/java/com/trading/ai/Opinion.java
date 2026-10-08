package com.trading.ai;

import java.util.List;

/**
 * 辩论观点：某个辩论方的立场、置信度与理由清单。
 */
public class Opinion {

    private final String speaker;
    private final String stance;       // "BULL" 或 "BEAR"
    private final double confidence;   // 0..1
    private final List<String> reasons;

    public Opinion(String speaker, String stance, double confidence, List<String> reasons) {
        this.speaker = speaker;
        this.stance = stance;
        this.confidence = confidence;
        this.reasons = reasons;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getStance() {
        return stance;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    @Override
    public String toString() {
        return "Opinion{" + speaker + "(" + stance + ", conf=" + String.format("%.2f", confidence)
                + ", reasons=" + reasons + ")}";
    }

}
