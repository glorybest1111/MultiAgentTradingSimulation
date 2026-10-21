package com.trading.simulation;

import com.trading.account.Account;
import com.trading.account.MarginMonitor;
import com.trading.account.MarginShortSellPolicy;
import com.trading.account.ShortSellingMode;
import com.trading.agent.DecisionLog;
import com.trading.agent.MarketSnapshot;
import com.trading.agent.TraderAgent;
import com.trading.agent.TradingApi;
import com.trading.engine.MatchingEngine;
import com.trading.market.KeywordSentimentAnalyzer;
import com.trading.market.NewsEngine;
import com.trading.market.NewsItem;
import com.trading.market.PriceHistory;
import com.trading.market.SentimentAnalyzer;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.Side;
import com.trading.model.Trade;
import com.trading.risk.CircuitBreaker;
import com.trading.risk.DefaultRiskController;
import com.trading.risk.FeeCalculator;
import com.trading.risk.PriceLimitRule;
import com.trading.risk.SlippageModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 市场仿真主循环：
 * 每期依次执行 新闻发布 → 价格随机游走 → 风控状态更新 → 止损触发 → 保证金/强平检查
 *         → Agent 决策与撮合 → 账户结算 → 业绩记录 → Dashboard 刷新。
 *
 * <p>价格过程受新闻情绪影响；Agent 通过 {@link MarketSnapshot} 观察市场，
 * 通过 {@link TradingApi} 下单。
 *
 * <p>对外 API（GUI / 外部系统驱动用）：
 * <ul>
 *   <li>{@link #addAgent(TraderAgent)}：注册交易者并开户</li>
 *   <li>{@link #step(int)}：推进一期</li>
 *   <li>{@link #start()}：连续推进 config.ticks 期</li>
 *   <li>{@link #submitOrder(Order)}：外部（GUI/管理员）直接下单</li>
 *   <li>{@link #injectNews(String)}：外部注入新闻</li>
 * </ul>
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
        // ---- 新增：风控与做空统计 ----
        public final int rejectedOrders;      // 被风控/保证金拒单的数量
        public final int liquidations;        // 强制平仓次数
        public final double borrowInterest;   // 累计借券利息
        public final int circuitBreakerHits;  // 熔断触发次数

        public Result(double finalPrice, double priceVolatility, double avgSpread, int totalVolume,
                      List<PerformanceTracker.Stats> ranking,
                      Map<String, List<Decision>> decisionsByAgent, List<Trade> lastTrades,
                      int rejectedOrders, int liquidations, double borrowInterest, int circuitBreakerHits) {
            this.finalPrice = finalPrice;
            this.priceVolatility = priceVolatility;
            this.avgSpread = avgSpread;
            this.totalVolume = totalVolume;
            this.ranking = ranking;
            this.decisionsByAgent = decisionsByAgent;
            this.lastTrades = lastTrades;
            this.rejectedOrders = rejectedOrders;
            this.liquidations = liquidations;
            this.borrowInterest = borrowInterest;
            this.circuitBreakerHits = circuitBreakerHits;
        }
    }

    private final SimulationConfig config;
    private final Random rng;
    private final PriceHistory history = new PriceHistory();
    private final SentimentAnalyzer sentiment;
    private final NewsEngine news;
    private final MatchingEngine engine;
    private final FeeCalculator fees;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final PerformanceTracker tracker = new PerformanceTracker();
    private final DecisionLog log;
    private final List<TraderAgent> agents = new ArrayList<>();
    private final Map<String, List<Decision>> decisionsByAgent = new LinkedHashMap<>();
    private final List<Trade> lastTrades = new ArrayList<>();

    private final Dashboard dashboard;
    private final CircuitBreaker circuitBreaker;
    private final DefaultRiskController riskController;
    private final MarginShortSellPolicy shortSellPolicy;
    private final MarginMonitor marginMonitor;

    private double price;
    private double referencePrice;
    private double mood;
    private NewsItem lastNews;
    private double spreadSum;
    private int spreadCount;
    private int totalVolume;
    private int currentTick;
    private double borrowInterestTotal;
    private int liquidationTotal;
    private int circuitBreakerHits;

    public MarketSimulation(SimulationConfig config) {
        this(config, Paths.get("data"), new KeywordSentimentAnalyzer());
    }

    public MarketSimulation(SimulationConfig config, Path logDir) {
        this(config, logDir, new KeywordSentimentAnalyzer());
    }

    public MarketSimulation(SimulationConfig config, Path logDir, SentimentAnalyzer analyzer) {
        this.config = config;
        this.sentiment = analyzer == null ? new KeywordSentimentAnalyzer() : analyzer;
        this.rng = new Random(config.seed);
        this.news = new NewsEngine(this.sentiment, rng, config.newsProbability);
        this.engine = new MatchingEngine(new SlippageModel(config.slippagePerLevel));
        this.fees = new FeeCalculator(config.feeRate);
        this.price = config.initialPrice;
        this.referencePrice = config.initialPrice;
        this.log = new DecisionLog(logDir, config.experimentName);
        this.dashboard = new Dashboard(config.symbol, config.dashboardInterval);

        history.add(price);
        engine.setLastPrice(price);

        // 风控：涨跌停 + 熔断
        this.circuitBreaker = new CircuitBreaker(config.circuitBreakerDropPct, config.circuitBreakerWindow,
                config.circuitBreakerHaltMillis, config.circuitBreakerHaltTicks, config.realTimeHalt);
        DefaultRiskController controller = config.riskControlEnabled
                ? new DefaultRiskController(new PriceLimitRule(config.priceLimitPct), circuitBreaker)
                : null;
        this.riskController = controller;
        engine.setValidator(controller);

        // 做空：MARGIN 模式才启用保证金校验，LEGACY 保持历史行为
        ShortSellingMode mode = config.shortSellingEnabled ? ShortSellingMode.MARGIN : ShortSellingMode.LEGACY;
        this.shortSellPolicy = new MarginShortSellPolicy(accounts, mode,
                config.initialMarginRate, config.maintenanceMarginRate);
        engine.setShortSellPolicy(shortSellPolicy);
        this.marginMonitor = new MarginMonitor(accounts, config.borrowInterestPerTick);
    }

    // ---------------- 参与者注册 ----------------

    /** 注册一个交易者并为其开户 */
    public void addAgent(TraderAgent agent) {
        agents.add(agent);
        Account account = new Account(agent.getName(), config.initialCash);
        account.setInitialMarginRate(config.initialMarginRate);
        account.setMaintenanceMarginRate(config.maintenanceMarginRate);
        accounts.put(agent.getName(), account);
        tracker.register(agent.getName(), config.initialCash);
        decisionsByAgent.put(agent.getName(), new ArrayList<>());
    }

    // ---------------- 对外驱动 API ----------------

    /** 连续推进全部周期（等价于旧的 run()） */
    public void start() {
        for (int t = 1; t <= config.ticks; t++) {
            step(t);
        }
    }

    /** 推进一期：GUI / Timer / 外部系统可以按自己节奏调用 */
    public void step(int tick) {
        tickInternal(tick);
    }

    /**
     * 外部直接下单（GUI 交易面板、管理员、AI 托管都走这里）。
     * 会经过风控与做空校验，成交后立即结算。
     */
    public List<Trade> submitOrder(Order order) {
        List<Trade> trades = engine.submitOrder(order, referencePrice, System.currentTimeMillis(), currentTick);
        for (Trade trade : trades) {
            settle(trade);
        }
        return trades;
    }

    /** 外部注入新闻（管理员控制台权限入口） */
    public NewsItem injectNews(String content) {
        NewsItem item = news.injectNews(content);
        if (item != null) {
            applyNewsImpact(item);
            lastNews = item;
        }
        return item;
    }

    /** 手动撤单（AI 托管自动撤单 / GUI 撤单） */
    public boolean cancelOrder(Order order) {
        return engine.cancelOrder(order);
    }

    // ---------------- 结果 ----------------

    public Result run() {
        start();
        return buildResult();
    }

    // ---------------- 主循环内部实现 ----------------

    private void tickInternal(int t) {
        currentTick = t;
        long nowMillis = System.currentTimeMillis();

        // 1) 新闻与情绪（newsProbability=0 即关闭新闻）
        if (config.newsProbability > 0) {
            NewsItem generated = news.maybeGenerate();
            if (generated != null) {
                applyNewsImpact(generated);
                lastNews = generated;
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

        // 3) 风控：熔断窗口价格采样（可能在本次触发熔断）
        boolean wasTripped = circuitBreaker.isTripped();
        circuitBreaker.onPrice(price, t, nowMillis);
        if (circuitBreaker.isTripped() && !wasTripped) {
            circuitBreakerHits++;
        }

        boolean halted = config.riskControlEnabled
                && circuitBreaker.isHalted(t, System.currentTimeMillis());

        if (!halted) {
            // 4) 止损单触发
            for (Trade trade : engine.triggerStopOrders(price, referencePrice, nowMillis)) {
                settle(trade);
            }

            // 5) 保证金：计提利息 + 强平检查
            borrowInterestTotal += marginMonitor.accrueInterest(price);
            for (Order liquidation : marginMonitor.checkMarginCalls(config.symbol, price, nowMillis)) {
                liquidationTotal++;
                for (Trade trade : engine.submitOrder(liquidation, referencePrice, nowMillis, t)) {
                    settle(trade);
                }
            }
        }

        // 6) 重置本期虚拟流动性深度并记录价差样本（Agent 行动前）
        engine.resetVirtualDepth();
        double spread = engine.getOrderBook().spread();
        if (!Double.isNaN(spread)) {
            spreadSum += spread;
            spreadCount++;
        }

        // 7) Agent 决策与交易（熔断暂停期间不下单）
        if (!halted) {
            for (TraderAgent agent : agents) {
                TradingApi api = new TradingApiImpl(agent.getName());
                Account account = accounts.get(agent.getName());
                MarketSnapshot snapshot = buildSnapshot(t, account, spread);
                Decision decision = agent.onTick(snapshot, api);
                if (decision != null) {
                    // 回填决策时的市价，供后续胜率与用户画像计算
                    decision.setDecisionPrice(price);
                    log.logDecision(t, decision);
                    decisionsByAgent.get(agent.getName()).add(decision);
                }
            }
        }

        // 8) 记录净值
        for (Account account : accounts.values()) {
            tracker.record(account.getName(), account.equity(price));
        }

        // 9) 实时 Dashboard
        if (dashboard.shouldRender(t)) {
            dashboard.render(t, price, referencePrice, mood, spread, accounts,
                    config.initialCash, lastNews, halted);
        }
    }

    private void applyNewsImpact(NewsItem item) {
        price *= 1 + item.getImpact() * config.newsShockScale;
        mood = clamp(mood * config.moodDecay + item.getImpact() * 0.5);
    }

    private MarketSnapshot buildSnapshot(int t, Account account, double spread) {
        return new MarketSnapshot(
                config.symbol, t, price, mood, config.sentimentAffectsStrategies,
                bestPrice(Side.BUY), bestPrice(Side.SELL), spread,
                history,
                account.getCash(), account.getPosition(), account.getAvgCost(),
                config.sentimentAffectsStrategies ? lastNews : null);
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
                tracker.stats(accounts, price), decisionsByAgent, new ArrayList<>(lastTrades),
                engine.getRejectedCount(), liquidationTotal, borrowInterestTotal, circuitBreakerHits);
    }

    // ---------------- 查询 API（GUI 数据面板用） ----------------

    public double getPrice() {
        return price;
    }

    public double getReferencePrice() {
        return referencePrice;
    }

    public double getMood() {
        return mood;
    }

    public NewsItem getLatestNews() {
        return lastNews;
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public int getTotalTicks() {
        return config.ticks;
    }

    public PriceHistory getHistory() {
        return history;
    }

    public NewsEngine getNewsEngine() {
        return news;
    }

    public MatchingEngine getEngine() {
        return engine;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public boolean isHalted() {
        return config.riskControlEnabled
                && circuitBreaker.isHalted(currentTick, System.currentTimeMillis());
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public DefaultRiskController getRiskController() {
        return riskController;
    }

    public MarginShortSellPolicy getShortSellPolicy() {
        return shortSellPolicy;
    }

    /** 所有账户（供管理员监控全市场资产） */
    public Map<String, Account> getAccounts() {
        return Collections.unmodifiableMap(accounts);
    }

    /** 单个账户；不存在时返回 null */
    public Account getAccount(String traderName) {
        return accounts.get(traderName);
    }

    /** 当前买卖价差，无有效价差时为 NaN */
    public double getSpread() {
        return engine.getOrderBook().spread();
    }

    /** 某个交易者尚未成交的挂单（GUI 撤单 / AI 托管撤单展示用） */
    public List<Order> openOrdersOf(String traderName) {
        return engine.openOrdersOf(traderName);
    }

    /** 某个 Agent 截至当前的决策记录（GUI 导出 CSV / 画像计算用） */
    public List<Decision> getDecisions(String agentName) {
        List<Decision> decisions = decisionsByAgent.get(agentName);
        return decisions == null ? List.of() : Collections.unmodifiableList(decisions);
    }

    /** 全部 Agent 的决策记录 */
    public Map<String, List<Decision>> getAllDecisions() {
        return Collections.unmodifiableMap(decisionsByAgent);
    }

    /** 最近的成交记录 */
    public List<Trade> getLastTrades() {
        return Collections.unmodifiableList(lastTrades);
    }

    /** 注册的交易者列表 */
    public List<TraderAgent> getAgents() {
        return Collections.unmodifiableList(agents);
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    /** TradingApi 实现：下单即撮合并结算。每个 Agent 持有自己的实例，用于识别挂单归属 */
    private class TradingApiImpl implements TradingApi {

        private final String holderName;

        TradingApiImpl(String holderName) {
            this.holderName = holderName;
        }

        @Override
        public Order placeOrder(Order order) {
            submitOrder(order);
            return order;
        }

        @Override
        public boolean cancelOrder(Order order) {
            return engine.cancelOrder(order);
        }

        @Override
        public List<Order> openOrders() {
            return openOrdersOf(holderName);
        }

        @Override
        public boolean isTradingHalted() {
            return MarketSimulation.this.isHalted();
        }
    }
}
