package com.trading.market;

import java.time.LocalDateTime;

/**
 * 市场新闻：标题 + 冲击力度（-1..1，正=利好，负=利空）。
 */
public class NewsItem {

    private final String headline;
    private final double impact;
    private final LocalDateTime time;

    public NewsItem(String headline, double impact) {
        this.headline = headline;
        this.impact = Math.max(-1, Math.min(1, impact));
        this.time = LocalDateTime.now();
    }

    public String getHeadline() {
        return headline;
    }

    public double getImpact() {
        return impact;
    }

    public LocalDateTime getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "News{headline='" + headline + "', impact=" + String.format("%+.2f", impact) + "}";
    }

}
