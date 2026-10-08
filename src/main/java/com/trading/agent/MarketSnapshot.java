package com.trading.agent;

import com.trading.market.NewsItem;
import com.trading.market.PriceHistory;

/**
 * 市场快照：每个交易周期传给各 Agent 的行情与账户状态。
 */
public class MarketSnapshot {

    private final String symbol;
    private final int tick;
    private final double price;
    private final double marketMood;     // -1..1 新闻情绪
    private final double bestBid;        // 无买单时 NaN
    private final double bestAsk;        // 无卖单时 NaN
    private final double spread;         // 无价差时 NaN
    private final PriceHistory history;
    private final double cash;
    private final int position;
    private final double avgCost;
    private final NewsItem latestNews;

    public MarketSnapshot(String symbol, int tick, double price, double marketMood,
                          double bestBid, double bestAsk, double spread,
                          PriceHistory history, double cash, int position, double avgCost,
                          NewsItem latestNews) {
        this.symbol = symbol;
        this.tick = tick;
        this.price = price;
        this.marketMood = marketMood;
        this.bestBid = bestBid;
        this.bestAsk = bestAsk;
        this.spread = spread;
        this.history = history;
        this.cash = cash;
        this.position = position;
        this.avgCost = avgCost;
        this.latestNews = latestNews;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getTick() {
        return tick;
    }

    public double getPrice() {
        return price;
    }

    public double getMarketMood() {
        return marketMood;
    }

    public double getBestBid() {
        return bestBid;
    }

    public double getBestAsk() {
        return bestAsk;
    }

    public boolean hasBid() {
        return !Double.isNaN(bestBid);
    }

    public boolean hasAsk() {
        return !Double.isNaN(bestAsk);
    }

    public double getSpread() {
        return spread;
    }

    public PriceHistory getHistory() {
        return history;
    }

    public double getCash() {
        return cash;
    }

    public int getPosition() {
        return position;
    }

    public double getAvgCost() {
        return avgCost;
    }

    public NewsItem getLatestNews() {
        return latestNews;
    }

}
