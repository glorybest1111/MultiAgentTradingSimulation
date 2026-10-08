package com.trading.market;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 价格历史：维护滚动窗口，提供均线、动量、波动率等指标。
 */
public class PriceHistory {

    private static final int MAX_SIZE = 300;

    private final Deque<Double> prices = new ArrayDeque<>();

    public void add(double price) {
        if (prices.size() >= MAX_SIZE) {
            prices.pollFirst();
        }
        prices.addLast(price);
    }

    public int size() {
        return prices.size();
    }

    public double latest() {
        return prices.isEmpty() ? Double.NaN : prices.peekLast();
    }

    /** 最近 n 期价格（旧→新），不足 n 期时返回全部 */
    public List<Double> recent(int n) {
        List<Double> all = new ArrayList<>(prices);
        if (all.size() <= n) {
            return all;
        }
        return all.subList(all.size() - n, all.size());
    }

    /** n 期简单移动平均；数据不足时返回 NaN */
    public double sma(int n) {
        List<Double> window = recent(n);
        if (window.size() < n) {
            return Double.NaN;
        }
        double sum = 0;
        for (double price : window) {
            sum += price;
        }
        return sum / n;
    }

    /** n 期动量 = (现价 - n 期前价格) / n 期前价格；数据不足时返回 NaN */
    public double momentum(int n) {
        List<Double> window = recent(n + 1);
        if (window.size() < n + 1) {
            return Double.NaN;
        }
        double past = window.get(0);
        double now = window.get(window.size() - 1);
        return (now - past) / past;
    }

    /** n 期收益率标准差（波动率）；数据不足时返回 NaN */
    public double volatility(int n) {
        List<Double> window = recent(n + 1);
        if (window.size() < n + 1) {
            return Double.NaN;
        }
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < window.size(); i++) {
            double prev = window.get(i - 1);
            returns.add((window.get(i) - prev) / prev);
        }
        double mean = 0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.size();
        double variance = 0;
        for (double r : returns) {
            variance += (r - mean) * (r - mean);
        }
        return Math.sqrt(variance / returns.size());
    }

}
