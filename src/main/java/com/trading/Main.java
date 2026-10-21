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
 * <p>实验 1：AI 策略竞赛（Momentum / Value / HFT / Debate / MarketMaker）
 * 实验 2：加入做市商前后对比（价差 / 成交量 / 价格波动率，全部为实测值）
 * 实验 3：AI 辩论 vs 单一 AI 策略（收益 / 风险）
 *
 * <p>运行方式：
 * <pre>
 *   java com.trading.Main            # 三个实验 + 可解释 AI 样本
 *   java com.trading.Main --human    # 控制台人机交互模式
 *   java com.trading.Main --gui      # JavaFX 图形界面（登录 / 管理员 / 用户终端）
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                java.nio.charset.StandardCharsets.UTF_8));
        String mode = args.length > 0 ? args[0] : "";
        switch (mode) {
            case "--human":
                runHumanDemo();
                return;
            case "--gui":
                launchGui();
                return;
            default:
                break;
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

    /** 反射加载 GUI，保证没有 JavaFX 依赖时命令行模式依旧可用 */
    private static void launchGui() {
        try {
            Class<?> launcher = Class.forName("com.trading.ui.TradingPlatformApplication");
            launcher.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (ClassNotFoundException e) {
            System.out.println("[GUI] 未找到 JavaFX 前端类 com.trading.ui.TradingPlatformApplication。");
            System.out.println("      请以 javafx 依赖的运行配置启动（见 README：mvn javafx:run），");
            System.out.println("      或继续使用命令行模式：java com.trading.Main");
        } catch (Exception e) {
            System.err.println("[GUI] 启动失败: " + e.getMessage());
        }
    }

    // ---------------- 实验 1：AI 策略竞赛 ----------------

    private static MarketSimulation.Result runCompetition() {
        SimulationConfig config = new SimulationConfig();
        config.ticks = 300;
        config.seed = 42;
        config.experimentName = "exp1-competition";

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
        System.out.printf("风控统计: 拒单 %d 笔 | 强制平仓 %d 次 | 熔断 %d 次 | 借券利息 $%.2f%n",
                result.rejectedOrders, result.liquidations, result.circuitBreakerHits, result.borrowInterest);
    }

    // ---------------- 实验 2：做市商前后对比 ----------------

    private static void printExperiment2() {
        System.out.println("【实验 2】加入 Market Maker 前后对比 (同一随机种子, 仅做市商开关不同)");
        System.out.println();
        SimulationConfig base = new SimulationConfig();
        base.ticks = 300;
        base.seed = 42;

        SimulationConfig offConfig = base.copy();
        offConfig.experimentName = "exp2-without-mm";
        SimulationConfig onConfig = base.copy();
        onConfig.experimentName = "exp2-with-mm";

        MarketSimulation.Result withoutMM = runMarketMakerExperiment(offConfig, false);
        MarketSimulation.Result withMM = runMarketMakerExperiment(onConfig, true);

        // 全部指标改为实测值 + 真实差值，不再写死文案
        String[] header = {"Metric", "Without MM", "With MM", "Change"};
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Avg Spread (book)", spreadText(withoutMM.avgSpread), spreadText(withMM.avgSpread),
                spreadChange(withoutMM, withMM)});
        rows.add(new String[]{"Avg Spread (implicit)", implicitSpreadText(base), implicitSpreadText(base),
                String.format("%+.2f%% (both runs share the same virtual-depth model)", 0.0)});
        rows.add(new String[]{"Total Volume", String.valueOf(withoutMM.totalVolume),
                String.valueOf(withMM.totalVolume), volumeChange(withoutMM, withMM)});
        rows.add(new String[]{"Price Volatility", String.format("%.2f%%", withoutMM.priceVolatility * 100),
                String.format("%.2f%%", withMM.priceVolatility * 100),
                volatilityChange(withoutMM, withMM)});
        rows.add(new String[]{"Avg Taker Return",
                String.format("%+.2f%%", avgTakerReturn(withoutMM) * 100),
                String.format("%+.2f%%", avgTakerReturn(withMM) * 100),
                takerReturnChange(withoutMM, withMM)});
        rows.add(new String[]{"Rejected Orders", String.valueOf(withoutMM.rejectedOrders),
                String.valueOf(withMM.rejectedOrders), ""});
        printTable(header, rows);
        System.out.println();
        System.out.println("注：无做市商时簿内通常没有双边报价，交易所水平的价差不存在，" +
                "此时真实执行成本来自滑点模型的虚拟深度（隐性价差 ≈ 2 × 每层恶化幅度）。");
        System.out.printf("做市商自身业绩: %s%n", findStats(withMM, "MarketMaker"));
    }

    private static MarketSimulation.Result runMarketMakerExperiment(SimulationConfig config, boolean marketMakerEnabled) {
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

    /** 实测价格波动率的相对变化，替代原先写死的 "unchanged" */
    private static String volatilityChange(MarketSimulation.Result before, MarketSimulation.Result after) {
        if (before.priceVolatility <= 0) {
            return "N/A";
        }
        double change = (after.priceVolatility - before.priceVolatility) / before.priceVolatility * 100;
        return String.format("%+.2f%% (price path is exogenous)", change);
    }

    /** 主动方平均收益的绝对变化（百分点），替代原先的定性描述 */
    private static String takerReturnChange(MarketSimulation.Result before, MarketSimulation.Result after) {
        double beforeReturn = avgTakerReturn(before);
        double afterReturn = avgTakerReturn(after);
        if (Double.isNaN(beforeReturn) || Double.isNaN(afterReturn)) {
            return "N/A";
        }
        double changePctPoints = (afterReturn - beforeReturn) * 100;
        return String.format("%+.2f pct points", changePctPoints);
    }

    private static String spreadText(double spread) {
        return Double.isNaN(spread) ? "N/A (no two-sided quotes)" : String.format("%.2f", spread);
    }

    private static String spreadChange(MarketSimulation.Result before, MarketSimulation.Result after) {
        if (Double.isNaN(before.avgSpread) || Double.isNaN(after.avgSpread)) {
            return Double.isNaN(after.avgSpread)
                    ? "N/A (no quotes in one run)"
                    : "n/a (baseline has no quotes)";
        }
        double change = (after.avgSpread - before.avgSpread) / before.avgSpread * 100;
        return String.format("%+.1f%%", change);
    }

    private static String implicitSpreadText(SimulationConfig config) {
        return String.format("%.2f", 2 * config.slippagePerLevel);
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
        System.out.println("【可解释 AI】每个 AI 的决策理由样本 (完整记录见 data/exp1-competition_decision_log.csv)");
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
        System.out.println("最近成交记录 (完整记录见 data/exp1-competition_trade_log.csv):");
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
        config.experimentName = "human-mode";
        config.dashboardInterval = 5;

        MarketSimulation simulation = new MarketSimulation(config);
        HumanTrader human = new HumanTrader();
        simulation.addAgent(human);
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
        System.out.println("运行产出 (按实验分文件):");
        System.out.println("  data/exp1-competition_decision_log.csv - 竞赛决策日志");
        System.out.println("  data/exp1-competition_trade_log.csv    - 竞赛成交日志");
        System.out.println("  data/exp2-without-mm_*.csv             - 无做市商对照组");
        System.out.println("  data/exp2-with-mm_*.csv                - 有做市商实验组");
        System.out.println("======================================================================");
    }
}
