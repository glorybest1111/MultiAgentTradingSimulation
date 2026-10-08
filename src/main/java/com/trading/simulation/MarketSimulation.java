package com.trading.simulation;

import com.trading.account.Account;
import com.trading.account.Portfolio;
import com.trading.agent.DecisionLog;
import com.trading.agent.MarketSnapshot;
import com.trading.agent.TraderAgent;
import com.trading.agent.TradingApi;
import com.trading.engine.MatchingEngine;
import com.trading.market.NewsEngine;
import com.trading.market.NewsItem;
import com.trading.market.PriceHistory;
import com.trading.market.SentimentAnalyzer;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.Side;
import com.trading.model.Trade;
import com.trading.risk.FeeCalculator;
import com.trading.risk.SlippageModel;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 市场仿真主循环：
 * 每期依次执行 新闻发布 → 价格随机游走 → 止损触发 → Agent 决策与撮合 → 账户结算 → 业绩记录。
 * 价格过程受新闻情绪影响；Agent 通过 MarketSnapshot 观察市场，通过 TradingApi 下单。
 */
public class MarketSimulation {

    /** 一次仿真的统计结果 */
    public static class Result {
        public final double finalPrice;
        public final double priceVolatility;
        public final double avgSpread;        // 无有效价差样本时为 NaN
        public final int totalVolume;
        public final List<PerformanceTracker.Stats> ranking;
        public final Map<String, List<Decision>> decisionsByAgent;
        public final List<Trade> lastTrades;

        public Result(double finalPrice, double priceVolatility, double avgSpread, int totalVolume,
                      List<PerformanceTracker.Stats> ranking,
                      Map<String, List<Decision>> decisionsByAgent, List<Trade> lastTrades) {
            this.finalPrice = finalPrice;
            this.priceVolatility = priceVolatility;
            this.avgSpread = avgSpread;
            this.totalVolume = totalVolume;
            this.ranking = ranking;
            this.decisionsByAgent = decisionsByAgent;
            this.lastTrades = lastTrades;
        }
    }

    private final SimulationConfig config;
    private final Random rng;
    private final PriceHistory history = new PriceHistory();
    private final SentimentAnalyzer sentiment = new SentimentAnalyzer();
    private final NewsEngine news;
    private final MatchingEngine engine;
    private final FeeCalculator fees;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Portfolio portfolio = new Portfolio();
    private final PerformanceTracker tracker = new PerformanceTracker();
    private final DecisionLog log = new DecisionLog(Paths.get("data"));
    private final List<TraderAgent> agents = new ArrayList<>();
    private final Map<String, List<Decision>> decisionsByAgent = new LinkedHashMap<>();
    private final List<Trade> lastTrades = new ArrayList<>();

    private double price;
    private double mood;
    private NewsItem lastNews;
    private double spreadSum;
    private int spreadCount;
    private int totalVolume;
    private int currentTick;

    public MarketSimulation(SimulationConfig config) {
        this.config = config;
        this.rng = new Random(config.seed);
        this.news = new NewsEngine(sentiment, rng, config.newsProbability);
        this.engine = new MatchingEngine(new SlippageModel(config.slippagePerLevel));
        this.fees = new FeeCalculator(config.feeRate);
        this.price = config.initialPrice;
        history.add(price);
        engine.setLastPrice(price);
    }

    /** 注册一个交易者并为其开户 */
    public void addAgent(TraderAgent agent) {
        agents.add(agent);
        Account account = new Account(agent.getName(), config.initialCash);
        accounts.put(agent.getName(), account);
        portfolio.add(account, config.initialCash);
        tracker.register(agent.getName(), config.initialCash);
        decisionsByAgent.put(agent.getName(), new ArrayList<>());
    }

    public Result run() {
        for (int t = 1; t <= config.ticks; t++) {
            tick(t);
        }
        return buildResult();
    }

    private void tick(int t) {
        currentTick = t;

        // 1) 新闻与情绪
        if (config.newsEnabled) {
            lastNews = news.maybeGenerate();
            if (lastNews != null) {
                price *= 1 + lastNews.getImpact() * config.newsShockScale;
                mood = clamp(mood * config.moodDecay + lastNews.getImpact() * 0.5);
            } else {
                mood *= config.moodDecay;
            }
        }

        // 2) 价格随机游走（按 0.01 最小变动价位取整）
        price = Math.max(1, price * (1 + config.drift
                + mood * config.moodScale
                + config.volatility * rng.nextGaussian()));
        price = Math.round(price * 100) / 100.0;
        history.add(price);
        engine.setLastPrice(price);

        // 3) 止损单触发
        for (Trade trade : engine.triggerStopOrders(price)) {
            settle(trade);
        }

        // 4) 重置本期虚拟流动性深度并记录价差样本（Agent 行动前）
        engine.resetVirtualDepth();
        double spread = engine.getOrderBook().spread();
        if (!Double.isNaN(spread)) {
            spreadSum += spread;
            spreadCount++;
        }

        // 5) Agent 决策与交易
        TradingApi api = new TradingApiImpl();
        for (TraderAgent agent : agents) {
            Account account = accounts.get(agent.getName());
            MarketSnapshot snapshot = new MarketSnapshot(
                    config.symbol, t, price, mood,
                    bestPrice(Side.BUY), bestPrice(Side.SELL), spread,
                    history,
                    account.getCash(), account.getPosition(), account.getAvgCost(),
                    lastNews);
            Decision decision = agent.onTick(snapshot, api);
            log.logDecision(t, decision);
            decisionsByAgent.get(agent.getName()).add(decision);
        }

        // 6) 记录净值
        for (Account account : accounts.values()) {
            tracker.record(account.getName(), account.equity(price));
        }
    }

    private double bestPrice(Side side) {
        Order best = side == Side.BUY ? engine.getOrderBook().getBestBid() : engine.getOrderBook().getBestAsk();
        return best == null ? Double.NaN : best.getPrice();
    }

    /** 结算一笔成交：双方账户现金/持仓更新 + 日志 */
    private void settle(Trade trade) {
        double fee = fees.fee(trade.getPrice() * trade.getQuantity());
        Account buyer = accounts.get(trade.getBuyTrader());
        Account seller = accounts.get(trade.getSellTrader());
        if (buyer != null) {
            buyer.applyFill(Side.BUY, trade.getPrice(), trade.getQuantity(), fee);
        }
        if (seller != null) {
            seller.applyFill(Side.SELL, trade.getPrice(), trade.getQuantity(), fee);
        }
        totalVolume += trade.getQuantity();
        log.logTrade(currentTick, trade);
        lastTrades.add(trade);
        if (lastTrades.size() > 10) {
            lastTrades.remove(0);
        }
    }

    private Result buildResult() {
        double avgSpread = spreadCount > 0 ? spreadSum / spreadCount : Double.NaN;
        List<Double> recent = history.recent(history.size());
        List<Double> logReturns = new ArrayList<>();
        for (int i = 1; i < recent.size(); i++) {
            logReturns.add(Math.log(recent.get(i) / recent.get(i - 1)));
        }
        double mean = 0;
        for (double r : logReturns) {
            mean += r;
        }
        mean /= Math.max(1, logReturns.size());
        double variance = 0;
        for (double r : logReturns) {
            variance += (r - mean) * (r - mean);
        }
        double priceVolatility = Math.sqrt(variance / Math.max(1, logReturns.size()));

        return new Result(price, priceVolatility, avgSpread, totalVolume,
                tracker.stats(accounts, price), decisionsByAgent, new ArrayList<>(lastTrades));
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    /** TradingApi 实现：下单即撮合并结算 */
    private class TradingApiImpl implements TradingApi {
        @Override
        public Order placeOrder(Order order) {
            for (Trade trade : engine.submitOrder(order)) {
                settle(trade);
            }
            return order;
        }

        @Override
        public boolean cancelOrder(Order order) {
            return engine.cancelOrder(order);
        }
    }

}
