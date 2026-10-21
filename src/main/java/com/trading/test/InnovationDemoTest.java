package com.trading.test;

import com.trading.agent.AiCustodyAgent;
import com.trading.agent.MarketSnapshot;
import com.trading.agent.TradingApi;
import com.trading.llm.LlmSentimentAnalyzer;
import com.trading.llm.MockLLMClient;
import com.trading.llm.PromptBuilder;
import com.trading.llm.StrategyDecision;
import com.trading.market.KeywordSentimentAnalyzer;
import com.trading.market.NewsItem;
import com.trading.market.PriceHistory;
import com.trading.market.SentimentAnalyzer;
import com.trading.model.Action;
import com.trading.model.Decision;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;
import com.trading.profile.ProfileTag;
import com.trading.profile.UserProfile;
import com.trading.profile.UserProfileCalculator;
import com.trading.risk.CircuitBreaker;
import com.trading.risk.MarketContext;
import com.trading.risk.PriceLimitRule;
import com.trading.risk.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 高级功能演示测试：无需 GUI 与网络，验证本次工程化扩展的全部新增能力。
 *
 * <pre>
 *   java com.trading.test.InnovationDemoTest
 * </pre>
 *
 * 覆盖：涨跌停 / 熔断 / 情绪分析可插拔 / LLM 投顾协议 / 用户画像标签 / AI 自动撤单。
 */
public class InnovationDemoTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== 高级功能演示测试 =====");
        testPriceLimit();
        testCircuitBreaker();
        testPluggableSentiment();
        testLlmDecisionProtocol();
        testUserProfileTags();
        testAiAutoCancel();
        System.out.println();
        System.out.printf("测试完成: 通过 %d 个, 失败 %d 个%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------- 模块一：风控 ----------------

    private static void testPriceLimit() {
        PriceLimitRule rule = new PriceLimitRule(0.10);
        MarketContext context = new MarketContext("AAPL", 150, 150, 1, System.currentTimeMillis());
        check(rule.check(new Order("AAPL", Side.BUY, OrderType.LIMIT, 160, 10, "T1"), context).isAccepted(),
                "1.1 涨停板内 (160 < 165) 正常通过");
        check(!rule.check(new Order("AAPL", Side.BUY, OrderType.LIMIT, 166, 10, "T1"), context).isAccepted(),
                "1.2 超过涨停价 +10% 被拒绝");
        check(!rule.check(new Order("AAPL", Side.SELL, OrderType.LIMIT, 134, 10, "T1"), context).isAccepted(),
                "1.3 低于跌停价 -10% 被拒绝");
    }

    private static void testCircuitBreaker() {
        // 仿真周期模式：离线实验不会永久暂停
        CircuitBreaker breaker = new CircuitBreaker(0.05, 5, 60_000, 3, false);
        long now = System.currentTimeMillis();
        breaker.onPrice(150, 1, now);
        breaker.onPrice(151, 2, now);
        check(!breaker.isHalted(2, now), "2.1 未出现大幅下跌时不熔断");
        breaker.onPrice(140, 3, now);   // 相对 151 下跌约 7.3%
        check(breaker.isHalted(3, now), "2.2 窗口内跌幅超过 5% 触发熔断");
        ValidationResult result = breaker.check(new MarketContext("AAPL", 140, 150, 3, now));
        check(!result.isAccepted(), "2.3 熔断期间订单被拒绝");
        check(breaker.isHalted(4, now) && breaker.isHalted(5, now), "2.4 暂停期内 (tick 4-5) 仍处于熔断");
        check(!breaker.isHalted(6, now), "2.5 暂停满 3 期后 (tick >= 6) 自动恢复交易");
    }

    // ---------------- 模块三前置：情绪分析可插拔 ----------------

    private static void testPluggableSentiment() {
        SentimentAnalyzer keyword = new KeywordSentimentAnalyzer();
        double positive = keyword.analyze("Apple reports record sales growth");
        double negative = keyword.analyze("Apple faces lawsuit and regulatory probe");
        check(positive > 0, "3.1 关键词版识别利好 (score=" + positive + ")");
        check(negative < 0, "3.2 关键词版识别利空 (score=" + negative + ")");

        // 同一个接口换成 LLM 实现（此处用本地 Mock 客户端），调用方代码无需改动
        SentimentAnalyzer llmAnalyzer = new LlmSentimentAnalyzer(new MockLLMClient());
        double llmScore = llmAnalyzer.analyze("Apple beats earnings estimates");
        check(!Double.isNaN(llmScore) && llmScore >= -1 && llmScore <= 1,
                "3.3 LLM 版实现同接口可替换且结果落在 [-1,1] (score=" + llmScore + ")");
        check(Math.abs(llmAnalyzer.analyze((NewsItem) null)) < 1e-9, "3.4 空新闻返回中性 0");
    }

    // ---------------- 模块三：LLM 投顾协议 ----------------

    private static void testLlmDecisionProtocol() {
        StrategyDecision buy = StrategyDecision.parse("ACTION=BUY QTY=12 CONFIDENCE=0.73 REASON=trend confirmed");
        check(buy.getAction() == Action.BUY && buy.getQuantity() == 12,
                "4.1 正常解析模型输出 BUY 12");
        check(Math.abs(buy.getConfidence() - 0.73) < 1e-9, "4.2 置信度解析正确");
        StrategyDecision invalid = StrategyDecision.parse("抱歉，我无法给出建议");
        check(invalid.getAction() == Action.HOLD, "4.3 异常输出安全降级为 HOLD");
        StrategyDecision zeroQty = StrategyDecision.parse("ACTION=SELL QTY=0 CONFIDENCE=0.5 REASON=none");
        check(zeroQty.getAction() == Action.HOLD, "4.4 数量为 0 的 SELL 降级为 HOLD");

        // 不同画像 → 不同 System Prompt
        UserProfile losing = profile(ProfileTag.AGGRESSIVE_LOSING);
        UserProfile winning = profile(ProfileTag.AGGRESSIVE_WINNING);
        PromptBuilder builder = new PromptBuilder();
        String conservativePrompt = builder.buildSystemPrompt(losing);
        String aggressivePrompt = builder.buildSystemPrompt(winning);
        check(conservativePrompt.contains("CONSERVATIVE"), "4.5 激进亏损用户得到互补保守型 Prompt");
        check(conservativePrompt.contains("Capital preservation"), "4.6 保守 Prompt 强调保本");
        check(aggressivePrompt.contains("AGGRESSIVE-SAME-FREQUENCY"), "4.7 激进盈利用户保持同频激进型 Prompt");
        check(!conservativePrompt.equals(aggressivePrompt), "4.8 不同画像的 Prompt 确实不同");

        // Mock 客户端按画像给出不同决策（保守型在中性行情下不出手）
        MockLLMClient mock = new MockLLMClient();
        String neutralBuy = mock.complete(conservativePrompt,
                builder.buildUserPrompt(150, 150, 0.0, 0.0, 0.01, 0, 100_000, 10));
        StrategyDecision neutralDecision = StrategyDecision.parse(neutralBuy);
        check(neutralDecision.getAction() == Action.HOLD, "4.9 保守 Prompt 在中性行情下选择观望");
    }

    // ---------------- 模块三：用户画像 ----------------

    private static void testUserProfileTags() {
        UserProfileCalculator calculator = new UserProfileCalculator();
        List<Decision> decisions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Decision decision = Decision.trade("Bob", Action.BUY, 1_000, 0.8, "chasing");
            decision.setDecisionPrice(150 + i * 2);   // 越买越高，当前价已跌回下方 → 判断为亏损决策
            decisions.add(decision);
        }
        UserProfile losing = calculator.calculate("Bob", decisions, 140, 150, 100_000, 90_000, 10);
        check(losing.getTag() == ProfileTag.AGGRESSIVE_LOSING,
                "5.1 高频大额 + 亏损 → " + losing.getTag().getCode());
        check(losing.getTurnoverRate() >= 0.30, "5.2 换手率计算正确 (" + losing.getTurnoverRate() + ")");
        check(losing.getExcessReturn() < 0, "5.3 相对大盘盈亏比为负");

        List<Decision> calm = new ArrayList<>();
        Decision one = Decision.trade("Alice", Action.BUY, 20, 0.6, "value");
        one.setDecisionPrice(140);
        calm.add(one);
        UserProfile winning = calculator.calculate("Alice", calm, 160, 150, 100_000, 120_000, 60);
        check(winning.getTag() == ProfileTag.CONSERVATIVE_WINNING,
                "5.4 低频小额 + 跑赢大盘 → " + winning.getTag().getCode());
        check(winning.getWinRate() > 0, "5.5 浮盈口径胜率 > 0");
        check(losing.getTag().getMaxNotionalRatio() < winning.getTag().getMaxNotionalRatio(),
                "5.6 亏损激进型单笔仓位上限被压得更低");
    }

    // ---------------- 模块三：AI 自动撤单 ----------------

    private static void testAiAutoCancel() {
        StubTradingApi api = new StubTradingApi();
        AiCustodyAgent custody = new AiCustodyAgent("Alice", new MockLLMClient());

        // 先挂一张未成交的限价买单
        Order resting = new Order("AAPL", Side.BUY, OrderType.LIMIT, 148, 20, "Alice");
        api.books.add(resting);

        PriceHistory history = new PriceHistory();
        for (double price = 150; price < 165; price += 1) {
            history.add(price);
        }
        MarketSnapshot bearish = new MarketSnapshot("AAPL", 10, 150, -0.75, true,
                149, 151, 2.0, history, 100_000, 0, 0, new NewsItem("Tech selloff deepens", -0.9));

        Decision decision = custody.onTick(bearish, api);
        check(decision.getAction() == Action.HOLD, "6.1 出现重大利空时 AI 不建新仓");
        check(api.cancelled == 1, "6.2 AI 自动撤销了用户的未成交限价单");
        check(decision.getReason().contains("auto-cancelled"), "6.3 决策理由记录自动撤单行为");

        // 情绪回暖且的重大利空消失后，AI 应能正常下单并给出画像标签理由
        MarketSnapshot normal = new MarketSnapshot("AAPL", 11, 150, 0.6, true,
                149, 151, 2.0, history, 100_000, 0, 0, null);
        Decision acted = custody.onTick(normal, api);
        check(acted.getReason().contains(custody.getProfile().getTag().getCode()),
                "6.4 决策理由携带用户画像标签，保证 LLM 决策可解释");
    }

    private static UserProfile profile(ProfileTag tag) {
        return new UserProfile("Bob", 10, 1000, 0.5, 20_000, -0.05, 0.01, -0.06, 0.3, tag);
    }

    // ---------------- 测试替身 ----------------

    /** 不依赖撮合引擎的最小 TradingApi，用于验证 AI 托管行为 */
    private static class StubTradingApi implements TradingApi {
        final List<Order> books = new ArrayList<>();
        int cancelled = 0;

        @Override
        public Order placeOrder(Order order) {
            return order;
        }

        @Override
        public boolean cancelOrder(Order order) {
            cancelled++;
            return books.remove(order);
        }

        @Override
        public List<Order> openOrders() {
            return new ArrayList<>(books);
        }

        @Override
        public boolean isTradingHalted() {
            return false;
        }
    }

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
