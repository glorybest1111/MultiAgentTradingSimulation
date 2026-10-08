package com.trading.simulation;

import com.trading.account.Account;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业绩跟踪器：记录每个 Agent 每期的净值序列，计算收益率与最大回撤。
 */
public class PerformanceTracker {

    /** 单个 Agent 的最终业绩统计 */
    public static class Stats {
        public final String name;
        public final double initialCash;
        public final double finalEquity;
        public final double returnRate;
        public final double maxDrawdown;
        public final double realizedPnl;
        public final double totalFees;
        public final int tradeCount;

        public Stats(String name, double initialCash, double finalEquity, double returnRate,
                     double maxDrawdown, double realizedPnl, double totalFees, int tradeCount) {
            this.name = name;
            this.initialCash = initialCash;
            this.finalEquity = finalEquity;
            this.returnRate = returnRate;
            this.maxDrawdown = maxDrawdown;
            this.realizedPnl = realizedPnl;
            this.totalFees = totalFees;
            this.tradeCount = tradeCount;
        }
    }

    private final Map<String, List<Double>> equitySeries = new LinkedHashMap<>();
    private final Map<String, Double> initialCash = new LinkedHashMap<>();

    public void register(String name, double initial) {
        equitySeries.put(name, new ArrayList<>());
        initialCash.put(name, initial);
    }

    public void record(String name, double equity) {
        List<Double> series = equitySeries.get(name);
        if (series != null) {
            series.add(equity);
        }
    }

    /** 各 Agent 按收益率降序的业绩统计 */
    public List<Stats> stats(Map<String, Account> accounts, double markPrice) {
        List<Stats> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : equitySeries.entrySet()) {
            String name = entry.getKey();
            List<Double> series = entry.getValue();
            double initial = initialCash.get(name);
            Account account = accounts.get(name);
            double finalEquity = account != null ? account.equity(markPrice) : initial;
            double returnRate = (finalEquity - initial) / initial;
            result.add(new Stats(
                    name, initial, finalEquity, returnRate,
                    maxDrawdown(series),
                    account != null ? account.getRealizedPnl() : 0,
                    account != null ? account.getTotalFees() : 0,
                    account != null ? account.getTradeCount() : 0));
        }
        result.sort(Comparator.comparingDouble((Stats s) -> s.returnRate).reversed());
        return result;
    }

    /** 最大回撤 = 净值序列的最大峰谷跌幅 */
    public static double maxDrawdown(List<Double> series) {
        if (series.isEmpty()) {
            return 0;
        }
        double peak = series.get(0);
        double maxDrawdown = 0;
        for (double equity : series) {
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, (peak - equity) / peak);
        }
        return maxDrawdown;
    }

}
