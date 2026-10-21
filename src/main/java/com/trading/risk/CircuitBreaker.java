package com.trading.risk;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 熔断机制：在滑动窗口内跌幅超过阈值时暂停该标的交易。
 *
 * <p>支持两种计时模式，由 {@code realTime} 决定：
 * <ul>
 *   <li>真实时钟模式：暂停 haltMillis 毫秒（GUI 实时模式适用，符合「暂停 1 分钟」语义）</li>
 *   <li>仿真周期模式：暂停 haltTicks 期（离线实验适用，否则 300 期会在几毫秒内跑完，
 *       真实 1 分钟将导致后续全部周期被永远熔断，实验结论失真）</li>
 * </ul>
 */
public class CircuitBreaker {

    private final Deque<Double> windowPrices = new ArrayDeque<>();
    private final double dropThreshold;
    private final int windowSize;
    private final long haltMillis;
    private final int haltTicks;
    private final boolean realTime;

    private boolean tripped;
    private long resumeAtMillis;
    private int resumeAtTick = Integer.MAX_VALUE;
    private String lastReason = "";

    public CircuitBreaker(double dropThreshold, int windowSize, long haltMillis, int haltTicks, boolean realTime) {
        this.dropThreshold = dropThreshold;
        this.windowSize = Math.max(2, windowSize);
        this.haltMillis = haltMillis;
        this.haltTicks = Math.max(1, haltTicks);
        this.realTime = realTime;
    }

    /** 每期用最新价更新状态，并自动判断是否触发/恢复熔断 */
    public void onPrice(double price, int tick, long nowMillis) {
        windowPrices.addLast(price);
        while (windowPrices.size() > windowSize) {
            windowPrices.pollFirst();
        }
        if (tripped) {
            checkResume(tick, nowMillis);
            return;
        }
        double peak = maxInWindow();
        if (peak > 0) {
            double drawdown = (peak - price) / peak;
            if (drawdown >= dropThreshold) {
                tripped = true;
                resumeAtMillis = nowMillis + haltMillis;
                resumeAtTick = tick + haltTicks;
                lastReason = String.format("circuit breaker: %.2f%% drawdown in %d ticks (>= %.2f%%)",
                        drawdown * 100, windowPrices.size(), dropThreshold * 100);
            }
        }
    }

    private void checkResume(int tick, long nowMillis) {
        boolean resume = realTime ? nowMillis >= resumeAtMillis : tick >= resumeAtTick;
        if (resume) {
            tripped = false;
            windowPrices.clear();
            resumeAtTick = Integer.MAX_VALUE;
        }
    }

    /** 当前是否处于熔断暂停状态 */
    public boolean isHalted(int tick, long nowMillis) {
        if (!tripped) {
            return false;
        }
        checkResume(tick, nowMillis);
        return tripped;
    }

    public ValidationResult check(MarketContext context) {
        if (isHalted(context.getTick(), context.getTimestampMillis())) {
            return ValidationResult.reject(lastReason.isEmpty()
                    ? "circuit breaker active: trading suspended"
                    : lastReason);
        }
        return ValidationResult.accept();
    }

    private double maxInWindow() {
        double max = Double.NaN;
        for (double price : windowPrices) {
            if (Double.isNaN(max) || price > max) {
                max = price;
            }
        }
        return max;
    }

    public double getDropThreshold() {
        return dropThreshold;
    }

    public boolean isTripped() {
        return tripped;
    }

    public String getLastReason() {
        return lastReason;
    }
}
