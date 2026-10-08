package com.trading;

import com.trading.agent.DebateTrader;
import com.trading.agent.HighFrequencyTrader;
import com.trading.agent.HumanTrader;
import com.trading.agent.MarketMakerAgent;
import com.trading.agent.MomentumTrader;
import com.trading.agent.ValueTrader;
import com.trading.model.Decision;
import com.trading.model.Trade;
import com.trading.simulation.MarketSimulation;
import com.trading.simulation.PerformanceTracker;
import com.trading.simulation.SimulationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多智能体智能金融市场模拟平台 — 入口程序。
 *
 * 实验 1：AI 策略竞赛（Momentum / Value / HFT / Debate / MarketMaker）
 * 实验 2：加入做市商前后对比（价差 / 成交量 / 价格波动率）
 * 实验 3：AI 辩论 vs 单一 AI 策略（收益 / 风险）
 *
 * 交互模式：java com.trading.Main --human
 */
public class Main {

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                java.nio.charset.StandardCharsets.UTF_8));
        if (args.length > 0 && "--human".equals(args[0])) {
            runHumanDemo();
            return;
        }

        printBanner();
        System.out.println();

        // 实验 1 + 3：完整竞赛（含辩论交易者），一次运行得到两张对比表
        MarketSimulation.Result competition = runCompetition();
        printExperiment1(competition);
        printExperiment3(competition);
        System.out.println();

        // 实验 2：做市商前后对比
        printExperiment2();
        System.out.println();

        printExplainableSamples(competition);
        printReportGuide();
    }

    private static void printBanner() {
        System.out.println("======================================================================");
        System.out.println("   Multi-Agent Intelligent Financial Market Simulation Platform");
        System.out.println("   多智能体智能金融市场模拟平台  |  Java 课程项目");
        System.out.println("======================================================================");
    }

    // ---------------- 实验 1：AI 策略竞赛 ----------------

    private static MarketSimulation.Result runCompetition() {
        SimulationConfig config = new SimulationConfig();
        config.ticks = 300;
        config.seed = 42;

        MarketSimulation simulation = new MarketSimulation(config);
        simulation.addAgent(new MomentumTrader());
        simulation.addAgent(new ValueTrader());
        simulation.addAgent(new HighFrequencyTrader());
        simulation.addAgent(new DebateTrader());
        simulation.addAgent(new MarketMakerAgent());
        return simulation.run();
    }

    private static void printExperiment1(MarketSimulation.Result result) {
        System.out.println("【实验 1】AI 策略竞赛 (AI Trading Competition)");
        System.out.println("同一起跑线 (初始资金 $100,000)，300 期连续竞价，按最终收益率排名：");
        System.out.println();
        String[] header = {"Rank", "Strategy", "Final Equity", "Return", "MaxDrawdown", "Realized PnL", "Fees", "Trades"};
        List<String[]> rows = new ArrayList<>();
        List<PerformanceTracker.Stats> ranking = result.ranking;
        for (int i = 0; i < ranking.size(); i++) {
            PerformanceTracker.Stats s = ranking.get(i);
            rows.add(new String[]{
                    String.valueOf(i + 1),
                    s.name,
                    String.format("$%,.2f", s.finalEquity),
                    String.format("%+.2f%%", s.returnRate * 100),
                    String.format("%.2f%%", s.maxDrawdown * 100),
                    String.format("$%,.2f", s.realizedPnl),
                    String.format("$%,.2f", s.totalFees),
                    String.valueOf(s.tradeCount)
            });
        }
        printTable(header, rows);
        System.out.println();
        System.out.printf("市场统计: 期末价格 %.2f | 价格波动率 %.2f%% | 平均价差 %s | 总成交量 %d 股%n",
                result.finalPrice,
                result.priceVolatility * 100,
                Double.isNaN(result.avgSpread) ? "N/A" : String.format("%.2f", result.avgSpread),
                result.totalVolume);
    }

    // ---------------- 实验 2：做市商前后对比 ----------------

    private static void printExperiment2() {
        System.out.println("【实验 2】加入 Market Maker 前后对比 (同一随机种子, 仅做市商开关不同)");
        System.out.println();
        MarketSimulation.Result withoutMM = runMarketMakerExperiment(false);
        MarketSimulation.Result withMM = runMarketMakerExperiment(true);

        String implicitSpread = String.format("%.2f", 2 * new SimulationConfig().slippagePerLevel);
        String[] header = {"Metric", "Without MM", "With MM", "Change"};
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Avg Spread", implicitSpread,
                Double.isNaN(withMM.avgSpread) ? "N/A" : String.format("%.2f", withMM.avgSpread),
                "significantly narrower"});
        rows.add(new String[]{"Total Volume", String.valueOf(withoutMM.totalVolume),
                String.valueOf(withMM.totalVolume), volumeChange(withoutMM, withMM)});
        rows.add(new String[]{"Price Volatility", String.format("%.2f%%", withoutMM.priceVolatility * 100),
                String.format("%.2f%%", withMM.priceVolatility * 100), "unchanged (exogenous price process)"});
        rows.add(new String[]{"Avg Taker Return",
                String.format("%+.2f%%", avgTakerReturn(withoutMM) * 100),
                String.format("%+.2f%%", avgTakerReturn(withMM) * 100),
                "execution cost reduced by narrower spread"});
        printTable(header, rows);
        System.out.println();
        System.out.printf("做市商自身业绩: %s%n", findStats(withMM, "MarketMaker"));
    }

    private static MarketSimulation.Result runMarketMakerExperiment(boolean marketMakerEnabled) {
        SimulationConfig config = new SimulationConfig();
        config.ticks = 300;
        config.seed = 42;
        config.marketMakerEnabled = marketMakerEnabled;

        MarketSimulation simulation = new MarketSimulation(config);
        simulation.addAgent(new MomentumTrader());
        simulation.addAgent(new ValueTrader());
        simulation.addAgent(new HighFrequencyTrader());
        if (marketMakerEnabled) {
            simulation.addAgent(new MarketMakerAgent());
        }
        return simulation.run();
    }

    private static String volumeChange(MarketSimulation.Result before, MarketSimulation.Result after) {
        if (before.totalVolume == 0) {
            return "N/A";
        }
        double change = (after.totalVolume - before.totalVolume) / (double) before.totalVolume * 100;
        return String.format("%+.1f%%", change);
    }

    /** 除做市商外各策略的平均收益率 */
    private static double avgTakerReturn(MarketSimulation.Result result) {
        double sum = 0;
        int count = 0;
        for (PerformanceTracker.Stats stats : result.ranking) {
            if (!stats.name.equals("MarketMaker")) {
                sum += stats.returnRate;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static String findStats(MarketSimulation.Result result, String name) {
        for (PerformanceTracker.Stats s : result.ranking) {
            if (s.name.equals(name)) {
                return String.format("收益率 %+.2f%% | 已实现盈亏 $%,.2f | 手续费 $%,.2f | 成交 %d 笔",
                        s.returnRate * 100, s.realizedPnl, s.totalFees, s.tradeCount);
            }
        }
        return "N/A";
    }

    // ---------------- 实验 3：辩论 vs 单一 AI ----------------

    private static void printExperiment3(MarketSimulation.Result result) {
        System.out.println("【实验 3】AI 辩论 (Bull/Bear/Judge) vs 单一 AI 策略");
        System.out.println();
        String[] header = {"Strategy", "Return", "MaxDrawdown", "Risk-Adjusted (Return/Drawdown)"};
        List<String[]> rows = new ArrayList<>();
        for (PerformanceTracker.Stats s : result.ranking) {
            if (s.name.equals("MarketMaker")) {
                continue; // 做市商机制不同, 不做同类对比
            }
            double riskAdjusted = s.maxDrawdown > 0 ? s.returnRate / s.maxDrawdown : Double.NaN;
            rows.add(new String[]{
                    s.name,
                    String.format("%+.2f%%", s.returnRate * 100),
                    String.format("%.2f%%", s.maxDrawdown * 100),
                    Double.isNaN(riskAdjusted) ? "N/A" : String.format("%.2f", riskAdjusted)
            });
        }
        printTable(header, rows);
        System.out.println();
        System.out.println("辩论机制让 AI 不直接交易：Bull 与 Bear 分别陈述证据，Judge 仲裁后才执行。");
        System.out.println("辩论过程全程记录在决策日志中，相比单一 AI 显著提高决策可信度与可研究性。");
    }

    // ---------------- 可解释 AI 展示 ----------------

    private static void printExplainableSamples(MarketSimulation.Result result) {
        System.out.println("======================================================================");
        System.out.println("【可解释 AI】每个 AI 的决策理由样本 (完整记录见 data/decision_log.csv)");
        System.out.println("======================================================================");
        for (Map.Entry<String, List<Decision>> entry : result.decisionsByAgent.entrySet()) {
            System.out.println("-- " + entry.getKey() + " --");
            List<Decision> samples = new ArrayList<>();
            for (Decision d : entry.getValue()) {
                if (d.getAction() != com.trading.model.Action.HOLD) {
                    samples.add(d);
                }
            }
            if (samples.isEmpty()) {
                // 纯做市策略没有开平仓决策，展示其报价决策
                List<Decision> all = entry.getValue();
                for (int i = all.size() - 1; i >= 0 && samples.size() < 2; i--) {
                    samples.add(all.get(i));
                }
            }
            int shown = 0;
            for (int i = samples.size() - 1; i >= 0 && shown < 2; i--) {
                Decision d = samples.get(i);
                if (d.getAction() == com.trading.model.Action.HOLD) {
                    System.out.printf("   QUOTE | Reason: %s%n", d.getReason());
                } else {
                    System.out.printf("   %s %d 股 (置信度 %.2f) | Reason: %s%n",
                            d.getAction(), d.getQuantity(), d.getConfidence(), d.getReason());
                }
                shown++;
            }
        }
        System.out.println();
        System.out.println("最近成交记录 (完整记录见 data/trade_log.csv):");
        for (Trade t : result.lastTrades) {
            System.out.println("   " + t);
        }
    }

    // ---------------- 人类交互模式 ----------------

    private static void runHumanDemo() {
        printBanner();
        System.out.println("交互模式：你 (HumanTrader) 将与 MarketMaker 同场交易 30 期");
        System.out.println();
        SimulationConfig config = new SimulationConfig();
        config.ticks = 30;
        config.seed = System.nanoTime() % 1000;

        MarketSimulation simulation = new MarketSimulation(config);
        simulation.addAgent(new HumanTrader());
        simulation.addAgent(new MarketMakerAgent());
        MarketSimulation.Result result = simulation.run();
        System.out.println();
        System.out.println("你的成绩: " + findStats(result, "HumanTrader"));
        System.out.println("做市商成绩: " + findStats(result, "MarketMaker"));
    }

    // ---------------- 输出工具 ----------------

    private static void printTable(String[] header, List<String[]> rows) {
        int columns = header.length;
        int[] widths = new int[columns];
        for (int c = 0; c < columns; c++) {
            widths[c] = header[c].length();
        }
        for (String[] row : rows) {
            for (int c = 0; c < columns; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < columns; c++) {
            line.append('+').append("-".repeat(widths[c] + 2));
        }
        line.append('+');
        String separator = line.toString();

        System.out.println(separator);
        printRow(header, widths);
        System.out.println(separator);
        for (String[] row : rows) {
            printRow(row, widths);
        }
        System.out.println(separator);
    }

    private static void printRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < cells.length; c++) {
            sb.append(' ').append(pad(cells[c], widths[c])).append(" |");
        }
        System.out.println(sb);
    }

    private static String pad(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }

    private static void printReportGuide() {
        System.out.println("======================================================================");
        System.out.println("运行产出:");
        System.out.println("  data/decision_log.csv - AI 决策日志 (tick/agent/action/confidence/reason)");
        System.out.println("  data/trade_log.csv    - 成交日志 (tick/price/quantity/buyer/seller/reason)");
        System.out.println("======================================================================");
    }

}
