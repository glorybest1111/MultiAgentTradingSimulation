package com.trading.account;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资组合：汇总所有交易账户，输出按收益率排序的排名与统计（AI Trading Competition 的排名数据源）。
 */
public class Portfolio {

    /** 账户摘要：一行排名数据 */
    public static class Summary {
        public final String name;
        public final double cash;
        public final int position;
        public final double equity;
        public final double returnRate;
        public final double realizedPnl;
        public final double fees;
        public final int trades;

        public Summary(String name, double cash, int position, double equity,
                       double returnRate, double realizedPnl, double fees, int trades) {
            this.name = name;
            this.cash = cash;
            this.position = position;
            this.equity = equity;
            this.returnRate = returnRate;
            this.realizedPnl = realizedPnl;
            this.fees = fees;
            this.trades = trades;
        }
    }

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, Double> initialCash = new LinkedHashMap<>();

    public void add(Account account, double initial) {
        accounts.put(account.getName(), account);
        initialCash.put(account.getName(), initial);
    }

    public Account get(String name) {
        return accounts.get(name);
    }

    public List<Account> accounts() {
        return new ArrayList<>(accounts.values());
    }

    /** 各账户按收益率降序的摘要 */
    public List<Summary> summarize(double markPrice) {
        List<Summary> result = new ArrayList<>();
        for (Account account : accounts.values()) {
            double initial = initialCash.get(account.getName());
            result.add(new Summary(
                    account.getName(),
                    account.getCash(),
                    account.getPosition(),
                    account.equity(markPrice),
                    account.returnRate(initial, markPrice),
                    account.getRealizedPnl(),
                    account.getTotalFees(),
                    account.getTradeCount()));
        }
        result.sort(Comparator.comparingDouble((Summary s) -> s.returnRate).reversed());
        return result;
    }

    /** 组合总净值 */
    public double totalEquity(double markPrice) {
        double total = 0;
        for (Account account : accounts.values()) {
            total += account.equity(markPrice);
        }
        return total;
    }

}
