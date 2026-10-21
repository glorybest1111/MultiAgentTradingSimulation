package com.trading.agent;

import com.trading.llm.LLMClient;
import com.trading.llm.MockLLMClient;
import com.trading.llm.PromptBuilder;
import com.trading.llm.StrategyDecision;
import com.trading.market.PriceHistory;
import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.profile.UserProfile;
import com.trading.profile.UserProfileCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能托管交易者（核心创新点：LLM 投顾 + 用户画像 + 自动撤单）。
 *
 * <p>每期流程：
 * <ol>
 *   <li>按需重算用户画像（换手率 / 相对大盘盈亏比 → 标签）</li>
 *   <li>突发利空时<b>自动撤销</b>该用户未成交的限价买单，
 *       避免利空行情下被对手一击成交（风险优先于收益）</li>
 *   <li>按画像动态生成 System Prompt，调用 {@link LLMClient} 得到交易指令</li>
 *   <li>按画像的单笔资金上限截断仓位后下单，并把模型原始输出写入决策理由（可解释）</li>
 * </ol>
 *
 * <p>熔断期间自动停手，且不替换用户原有资金账户：它与 {@link HumanTrader}
 * 共享同一个用户名/账户，通过 {@code UserTerminalAgent} 决定谁当期值机。
 */
public class AiCustodyAgent implements TraderAgent {

    private static final double BAD_NEWS_MOOD = -0.40;      // 触发自动撤单的情绪阈值
    private static final int PROFILE_REFRESH_INTERVAL = 20; // 每 20 期重算一次画像

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final UserProfileCalculator profileCalculator = new UserProfileCalculator();
    private final OrderHistory history = new OrderHistory();
    private final String traderName;

    private static final double DEFAULT_REFERENCE_PRICE = 150;
    private static final double DEFAULT_INITIAL_CASH = 100_000;

    private double initialCash = DEFAULT_INITIAL_CASH;
    private double referencePrice = DEFAULT_REFERENCE_PRICE;
    private volatile UserProfile profile;
    private int lastRefreshTick = -1;
    private int autoCancelCount;

    public AiCustodyAgent(String traderName) {
        this(traderName, new MockLLMClient());
    }

    public AiCustodyAgent(String traderName, LLMClient llmClient) {
        this.traderName = traderName;
        this.llmClient = llmClient == null ? new MockLLMClient() : llmClient;
    }

    @Override
    public String getName() {
        return traderName;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public int getAutoCancelCount() {
        return autoCancelCount;
    }

    /** 供 {@code UserTerminalAgent} 把用户手动下单也计入画像统计 */
    public void recordManualOrder(Order order) {
        history.record(order);
    }

    public void recordDecisions(List<Decision> decisions) {
        for (Decision decision : decisions) {
            history.record(decision);
        }
    }

    /** 手动刷新画像（GUI 上打开 AI 托管时调用一次） */
    public UserProfile refreshProfile(MarketSnapshot snapshot) {
        List<Decision> recorded = history.asDecisions();
        double equity = snapshot.getCash() + snapshot.getPosition() * snapshot.getPrice();
        UserProfile calculated = profileCalculator.calculate(
                traderName, recorded, snapshot.getPrice(), referencePrice,
                initialCash, equity, Math.max(1, snapshot.getTick()));
        profile = calculated;
        return calculated;
    }

    public void setInitialCash(double initialCash) {
        this.initialCash = initialCash > 0 ? initialCash : DEFAULT_INITIAL_CASH;
    }

    public void setReferencePrice(double referencePrice) {
        this.referencePrice = referencePrice > 0 ? referencePrice : DEFAULT_REFERENCE_PRICE;
    }

    public double getInitialCash() {
        return initialCash;
    }

    public double getReferencePrice() {
        return referencePrice;
    }

    @Override
    public Decision onTick(MarketSnapshot snapshot, TradingApi api) {
        PriceHistory priceHistory = snapshot.getHistory();

        // 1) 画像：定期或首次计算
        if (profile == null || snapshot.getTick() - lastRefreshTick >= PROFILE_REFRESH_INTERVAL) {
            refreshProfile(snapshot);
            lastRefreshTick = snapshot.getTick();
        }

        // 2) 自动撤单：突发利空时撤销自己的未成交挂单
        double mood = snapshot.getMarketMood();
        if (api.isTradingHalted()) {
            return Decision.hold(getName(), "AI custody paused: circuit breaker active");
        }
        if (mood <= BAD_NEWS_MOOD) {
            List<Order> cancelled = cancelRestingOrders(api);
            if (!cancelled.isEmpty()) {
                autoCancelCount += cancelled.size();
                return Decision.hold(getName(), String.format(
                        "auto-cancelled %d resting order(s): breaking bad news (mood %.2f <= %.2f)",
                        cancelled.size(), mood, BAD_NEWS_MOOD));
            }
        }

        // 3) 依据画像组装 Prompt 并向大模型提问
        double trend = priceHistory.momentum(10);
        if (Double.isNaN(trend)) {
            trend = 0;
        }
        double volatility = priceHistory.volatility(10);
        if (Double.isNaN(volatility)) {
            volatility = 0.01;
        }

        UserProfile current = profile;
        int maxQuantity = maxQuantityFor(snapshot, current);
        String systemPrompt = promptBuilder.buildSystemPrompt(current);
        String userPrompt = promptBuilder.buildUserPrompt(snapshot.getPrice(), snapshot.getPrice(),
                mood, trend, volatility, snapshot.getPosition(), snapshot.getCash(), maxQuantity);
        String raw = llmClient.complete(systemPrompt, userPrompt);
        StrategyDecision decision = StrategyDecision.parse(raw);

        String tagCode = current == null ? "unknown" : current.getTag().getCode();
        String explainableReason = String.format("[%s|%s] %s", tagCode, llmClient.name(), decision.getReason());

        if (decision.getAction() == Action.HOLD || decision.getQuantity() <= 0) {
            return Decision.hold(getName(), explainableReason);
        }

        boolean isBuy = decision.getAction() == Action.BUY;
        int quantity = Math.min(decision.getQuantity(), maxQuantity);
        if (isBuy) {
            quantity = (int) Math.min(quantity, snapshot.getCash() / snapshot.getPrice());
        } else {
            quantity = Math.min(quantity, Math.max(0, snapshot.getPosition()));
        }
        if (quantity <= 0) {
            return Decision.hold(getName(), explainableReason + " (insufficient cash/position to act)");
        }

        Order order = new Order(snapshot.getSymbol(), isBuy ? Side.BUY : Side.SELL, OrderType.MARKET,
                snapshot.getPrice(), quantity, getName());
        api.placeOrder(order);
        history.record(order);
        return Decision.trade(getName(), decision.getAction(), quantity, decision.getConfidence(), explainableReason);
    }

    /** 撤销当前挂单（限价单；止损单保留以维持风控保护） */
    private List<Order> cancelRestingOrders(TradingApi api) {
        List<Order> cancelled = new ArrayList<>();
        for (Order order : api.openOrders()) {
            if (order.getType() == OrderType.LIMIT) {
                if (api.cancelOrder(order)) {
                    cancelled.add(order);
                }
            }
        }
        return cancelled;
    }

    /** 按画像限制单笔资金占用：EQUITY × 标签上限 / 价格 */
    private int maxQuantityFor(MarketSnapshot snapshot, UserProfile current) {
        double equity = snapshot.getCash() + snapshot.getPosition() * snapshot.getPrice();
        double ratio = current == null ? 0.05 : current.getTag().getMaxNotionalRatio();
        double budget = equity * ratio;
        if (budget <= 0 || snapshot.getPrice() <= 0) {
            return 0;
        }
        return (int) Math.floor(budget / snapshot.getPrice());
    }

    /** 轻量行为记录：把 Orders/Decisions 统一转换成决策序列供画像计算 */
    private static class OrderHistory {
        private final List<Decision> recorded = new ArrayList<>();

        void record(Order order) {
            if (order == null) {
                return;
            }
            recorded.add(new Decision("manual",
                    order.getSide() == Side.BUY ? Action.BUY : Action.SELL,
                    order.getQuantity(), 1.0, "manual order"));
        }

        void record(Decision decision) {
            if (decision != null) {
                recorded.add(decision);
            }
        }

        List<Decision> asDecisions() {
            return new ArrayList<>(recorded);
        }
    }
}
