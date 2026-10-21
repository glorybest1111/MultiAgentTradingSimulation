package com.trading.test;

import com.trading.agent.DebateTrader;
import com.trading.agent.HighFrequencyTrader;
import com.trading.agent.MarketMakerAgent;
import com.trading.agent.MomentumTrader;
import com.trading.agent.ValueTrader;
import com.trading.simulation.MarketSimulation;
import com.trading.simulation.SimulationConfig;

/**
 * 仿真冒烟测试：完整跑一遍多 Agent 仿真，验证系统端到端可运行。
 *   java com.trading.test.SimulationSmokeTest
 */
public class SimulationSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===== 仿真冒烟测试 =====");

        SimulationConfig config = new SimulationConfig();
        config.ticks = 150;
        config.seed = 7;
        config.experimentName = "smoketest-main";
        config.dashboardInterval = 0;

        MarketSimulation simulation = new MarketSimulation(config);
        simulation.addAgent(new MomentumTrader());
        simulation.addAgent(new ValueTrader());
        simulation.addAgent(new HighFrequencyTrader());
        simulation.addAgent(new DebateTrader());
        simulation.addAgent(new MarketMakerAgent());

        MarketSimulation.Result result = simulation.run();

        check(result.finalPrice > 0, "期末价格为正");
        check(result.ranking.size() == 5, "5 个 Agent 均完成仿真并生成排名");
        check(result.totalVolume > 0, "仿真期间发生了成交");
        check(!Double.isNaN(result.avgSpread), "存在有效买卖价差样本 (做市商提供流动性)");
        check(result.priceVolatility >= 0, "价格波动率计算有效");
        check(result.decisionsByAgent.size() == 5, "每个 Agent 都产出了可解释决策日志");

        for (var stats : result.ranking) {
            check(Double.isFinite(stats.returnRate), stats.name + " 收益率有限: " + String.format("%+.2f%%", stats.returnRate * 100));
        }

        // 做市商前后对比实验（相同 3 个策略，实验变量仅为是否注册做市商）
        SimulationConfig off = config.copy();
        off.experimentName = "smoketest-without-mm";
        MarketSimulation simOff = new MarketSimulation(off);
        simOff.addAgent(new MomentumTrader());
        simOff.addAgent(new ValueTrader());
        simOff.addAgent(new HighFrequencyTrader());
        MarketSimulation.Result resultOff = simOff.run();
        check(resultOff.ranking.size() == 3, "无做市商场景 3 个 Agent 正常完成");
        check(Double.isNaN(resultOff.avgSpread), "无做市商时簿内无价差 (滑点模型提供隐性流动性)");

        SimulationConfig on = config.copy();
        on.experimentName = "smoketest-with-mm";
        MarketSimulation simWithMM = new MarketSimulation(on);
        simWithMM.addAgent(new MomentumTrader());
        simWithMM.addAgent(new ValueTrader());
        simWithMM.addAgent(new HighFrequencyTrader());
        simWithMM.addAgent(new MarketMakerAgent());
        MarketSimulation.Result resultWithMM = simWithMM.run();
        check(!Double.isNaN(resultWithMM.avgSpread), "有做市商时存在有效买卖价差");
        check(resultWithMM.totalVolume > resultOff.totalVolume,
                "加入做市商后成交量提高 (" + resultOff.totalVolume + " -> " + resultWithMM.totalVolume + ")");

        // 情绪开关对照：关闭情绪后行为退化为纯技术面版本，不应崩溃
        SimulationConfig noSentiment = config.copy();
        noSentiment.experimentName = "smoketest-no-sentiment";
        noSentiment.sentimentAffectsStrategies = false;
        noSentiment.ticks = 60;
        MarketSimulation simNoSentiment = new MarketSimulation(noSentiment);
        simNoSentiment.addAgent(new MomentumTrader());
        simNoSentiment.addAgent(new ValueTrader());
        simNoSentiment.addAgent(new HighFrequencyTrader());
        MarketSimulation.Result sentimentOffResult = simNoSentiment.run();
        check(sentimentOffResult.ranking.size() == 3, "关闭情绪开关后仿真正常完成 (对照组)");

        // 风控：涨跌停与熔断不应导致仿真中断
        SimulationConfig guarded = config.copy();
        guarded.experimentName = "smoketest-risk-control";
        guarded.ticks = 100;
        guarded.priceLimitPct = 0.02;
        guarded.circuitBreakerDropPct = 0.02;
        guarded.circuitBreakerWindow = 5;
        MarketSimulation simGuarded = new MarketSimulation(guarded);
        simGuarded.addAgent(new MomentumTrader());
        simGuarded.addAgent(new MarketMakerAgent());
        MarketSimulation.Result guardedResult = simGuarded.run();
        check(guardedResult.finalPrice > 0, "开启风控后仿真仍能跑完");
        check(guardedResult.rejectedOrders >= 0, "风控拒单计数可用");

        System.out.println();
        System.out.printf("测试完成: 通过 %d 个, 失败 %d 个%n", passed, failed);
        System.out.println();
        System.out.println("排名预览:");
        for (var stats : result.ranking) {
            System.out.printf("  %-16s %+8.2f%%  (回撤 %.2f%%, 成交 %d 笔)%n",
                    stats.name, stats.returnRate * 100, stats.maxDrawdown * 100, stats.tradeCount);
        }
        if (failed > 0) {
            System.exit(1);
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
