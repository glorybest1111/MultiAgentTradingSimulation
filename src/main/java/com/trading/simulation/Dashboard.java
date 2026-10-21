package com.trading.simulation;

import com.trading.account.Account;
import com.trading.market.NewsItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 控制台实时 Dashboard：每 N 期由 {@link MarketSimulation} 在 tick 末尾调用。
 *
 * <p>展示内容：当前期数、最新价、相对参考价涨跌幅、市场情绪、买卖价差、
 * 是否熔断，以及按收益率实时排序的 Agent 排名（现金 / 持仓 / 净值 / 收益率 / 回撤）。
 *
 * <p>设计为独立的输出类，GUI 模式下把 {@code enabled} 设为 false 即可完全静默，
 * 不影响仿真逻辑。
 */
public class Dashboard {

    private final String symbol;
    private final int interval;
    private final Comparator<RankRow> byReturn = Comparator.comparingDouble((RankRow r) -> r.returnRate).reversed();

    /** 一行实时排名数据 */
    private static class RankRow {
        final String name;
        final double cash;
        final int position;
        final double equity;
        final double returnRate;
        final double drawdown;

        RankRow(String name, double cash, int position, double equity, double returnRate, double drawdown) {
            this.name = name;
            this.cash = cash;
            this.position = position;
            this.equity = equity;
            this.returnRate = returnRate;
            this.drawdown = drawdown;
        }
    }

    public Dashboard(String symbol, int interval) {
        this.symbol = symbol;
        this.interval = Math.max(0, interval);
    }

    public boolean shouldRender(int tick) {
        return interval > 0 && tick % interval == 0;
    }

    /**
     * 打印一帧 Dashboard。
     *
     * @param tick           当前期数
     * @param price          最新价
     * @param referencePrice 参考价（初始价），用于计算涨跌幅与涨跌停区间
     * @param mood           市场情绪
     * @param spread         买卖价差，无有效价差时为 NaN
     * @param accounts       所有账户
     * @param initialCash    初始资金
     * @param lastNews       最近新闻
     * @param halted         是否处于熔断暂停
     */
    public void render(int tick, double price, double referencePrice, double mood, double spread,
                       Map<String, Account> accounts, double initialCash, NewsItem lastNews, boolean halted) {
        String separator = "=".repeat(72);
        System.out.println();
        System.out.println(separator);
        System.out.printf("  [Dashboard] %s  tick=%d  last=%.2f  chg=%+.2f%%  mood=%+.2f  spread=%s%s%n",
                symbol, tick, price,
                referencePrice > 0 ? (price - referencePrice) / referencePrice * 100 : 0,
                mood,
                Double.isNaN(spread) ? "N/A" : String.format("%.2f", spread),
                halted ? "   [HALTED: circuit breaker]" : "");
        if (lastNews != null) {
            System.out.println("  Latest news: " + lastNews);
        }
        System.out.println(separator);
        System.out.printf("  %-16s %12s %8s %14s %10s %9s%n",
                "Strategy", "Cash", "Pos", "Equity", "Return", "MaxDD");
        System.out.println("-".repeat(72));

        List<RankRow> rows = new ArrayList<>();
        for (Account account : accounts.values()) {
            double equity = account.equity(price);
            rows.add(new RankRow(account.getName(), account.getCash(), account.getPosition(), equity,
                    (equity - initialCash) / initialCash, currentDrawdown(equity, initialCash)));
        }
        rows.sort(byReturn);
        int rank = 1;
        for (RankRow row : rows) {
            System.out.printf("  %d.%-13s %12.2f %8d %14.2f %+9.2f%% %8.2f%%%n",
                    rank++, row.name, row.cash, row.position, row.equity,
                    row.returnRate * 100, row.drawdown * 100);
        }
        System.out.println(separator);
    }

    /** 相对初始资金的即时回撤（Dashboard 版本不做序列扫描，只按初始资金算最大回撤上界） */
    private double currentDrawdown(double equity, double initialCash) {
        if (equity >= initialCash || initialCash <= 0) {
            return 0;
        }
        return (initialCash - equity) / initialCash;
    }

    public int getInterval() {
        return interval;
    }
}
