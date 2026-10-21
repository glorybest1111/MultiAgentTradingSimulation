package com.trading.account;

import com.trading.model.Side;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 交易账户：现金 + 持仓 + 已实现盈亏 + 手续费 + 空头/保证金。
 *
 * <p>多头用正数表示，空头用负数表示（{@link #getPosition()} 返回净持仓）；
 * 同时单独维护 {@code shortPosition} / {@code borrowedShares} / {@code margin} /
 * {@code loanInterest} 四个字段，用于做空额度、保证金占用与强平风控。
 *
 * <p>账户状态是本系统的「钱」，所有变更方法均为线程安全
 * （GUI 线程、Timer 线程、AI 托管线程可能并发操作同一账户）。
 */
public class Account {

    private final ReentrantLock lock = new ReentrantLock();

    private final String name;
    private double cash;
    private int position;            // 净持仓：正 = 多头，负 = 空头
    private double avgCost;          // 持仓均价（加权平均成本）
    private double realizedPnl;      // 已实现盈亏
    private double totalFees;        // 累计手续费
    private int tradeCount;          // 累计成交笔数

    // ---------------- 做空相关 ----------------
    private int shortPosition;       // 当前空头持仓绝对数量（借券未平数量）
    private int borrowedShares;      // 累计借入股票数量
    private double margin;           // 被占用的保证金
    private double loanInterest;     // 累计借券利息

    public Account(String name, double initialCash) {
        this.name = name;
        this.cash = initialCash;
    }

    // ---------------- 结算 ----------------

    /**
     * 结算一笔成交：买方按成交额 + 手续费扣现金，卖方按成交额 - 手续费收现金。
     */
    public void applyFill(Side side, double price, int quantity, double fee) {
        lock.lock();
        try {
            totalFees += fee;
            tradeCount++;
            if (side == Side.BUY) {
                buy(price, quantity);
                cash -= price * quantity + fee;
            } else {
                sell(price, quantity);
                cash += price * quantity - fee;
            }
        } finally {
            lock.unlock();
        }
    }

    private void buy(double price, int quantity) {
        if (position < 0) {
            // 先平空头（买券还券）
            int cover = Math.min(-position, quantity);
            realizedPnl += (avgCost - price) * cover;
            position += cover;
            shortPosition = Math.max(0, shortPosition - cover);
            releaseMargin(cover, avgCost);
            if (position == 0) {
                avgCost = 0;
            }
            int rest = quantity - cover;
            if (rest > 0) {
                openPosition(price, rest);
            }
        } else {
            openPosition(price, quantity);
        }
    }

    private void sell(double price, int quantity) {
        if (position > 0) {
            // 先平多头
            int close = Math.min(position, quantity);
            realizedPnl += (price - avgCost) * close;
            position -= close;
            if (position == 0) {
                avgCost = 0;
            }
            int rest = quantity - close;
            if (rest > 0) {
                openShort(price, rest);
            }
        } else {
            // 已有空头或空仓 → 建立/加大空头
            openShort(price, quantity);
        }
    }

    private void openPosition(double price, int quantity) {
        double oldQuantity = position;
        avgCost = (avgCost * oldQuantity + price * quantity) / (oldQuantity + quantity);
        position += quantity;
    }

    private void openShort(double price, int quantity) {
        if (position < 0) {
            avgCost = (avgCost * (-position) + price * quantity) / (-position + quantity);
        } else {
            avgCost = price;
        }
        position -= quantity;
        shortPosition += quantity;
        borrowedShares += quantity;
    }

    private void releaseMargin(int coveredQuantity, double avgPrice) {
        if (margin <= 0 || borrowedShares <= 0) {
            return;
        }
        double released = avgPrice * coveredQuantity * (margin / Math.max(1, borrowedShares * avgPrice));
        margin = Math.max(0, margin - released);
    }

    // ---------------- 保证金 ----------------

    /** 卖出开仓（或加大空头）所需的持仓保证金总额 */
    public double requiredMargin(double price, int additionalShortQuantity) {
        lock.lock();
        try {
            int projectedShort = shortPosition + additionalShortQuantity;
            return projectedShort * price * initialMarginRate;
        } finally {
            lock.unlock();
        }
    }

    private double initialMarginRate = 0.50;
    private double maintenanceMarginRate = 0.30;

    public void setInitialMarginRate(double rate) {
        lock.lock();
        try {
            this.initialMarginRate = rate;
        } finally {
            lock.unlock();
        }
    }

    public void setMaintenanceMarginRate(double rate) {
        lock.lock();
        try {
            this.maintenanceMarginRate = rate;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 冻结并占用开空所需的保证金。若现金不足以缴纳则失败。
     */
    public boolean reserveMargin(double price, int quantity) {
        lock.lock();
        try {
            double need = price * quantity * initialMarginRate;
            if (cash < need) {
                return false;
            }
            cash -= need;
            margin += need;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 归还保证金（平空后由外部调用） */
    public double releaseAllMargin() {
        lock.lock();
        try {
            double released = margin;
            cash += released;
            margin = 0;
            return released;
        } finally {
            lock.unlock();
        }
    }

    /** 计提一期借券利息 */
    public double accrueBorrowInterest(double price, double perTickRate) {
        lock.lock();
        try {
            if (shortPosition <= 0) {
                return 0;
            }
            double interest = shortPosition * price * perTickRate;
            loanInterest += interest;
            cash -= interest;
            return interest;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 维持保证金检查：权益 / 空头市值 低于维持比例时判定为爆仓。
     * 无空头时永远返回 false。
     */
    public boolean isMarginCall(double markPrice) {
        lock.lock();
        try {
            if (shortPosition <= 0) {
                return false;
            }
            double exposure = shortPosition * markPrice;
            if (exposure <= 0) {
                return false;
            }
            double equityValue = equity(markPrice) + margin;
            return equityValue / exposure < maintenanceMarginRate;
        } finally {
            lock.unlock();
        }
    }

    // ---------------- 查询 ----------------

    /** 净值 = 现金 + 持仓市值（按市价标记） */
    public double equity(double markPrice) {
        lock.lock();
        try {
            return cash + position * markPrice;
        } finally {
            lock.unlock();
        }
    }

    /** 总权益 = 现金 + 市值 + 被占用的保证金 */
    public double totalEquity(double markPrice) {
        lock.lock();
        try {
            return cash + margin + position * markPrice;
        } finally {
            lock.unlock();
        }
    }

    /** 收益率 = (净值 - 初始资金) / 初始资金 */
    public double returnRate(double initialCash, double markPrice) {
        return (equity(markPrice) - initialCash) / initialCash;
    }

    public String getName() {
        return name;
    }

    public double getCash() {
        lock.lock();
        try {
            return cash;
        } finally {
            lock.unlock();
        }
    }

    public int getPosition() {
        lock.lock();
        try {
            return position;
        } finally {
            lock.unlock();
        }
    }

    public double getAvgCost() {
        lock.lock();
        try {
            return avgCost;
        } finally {
            lock.unlock();
        }
    }

    public double getRealizedPnl() {
        lock.lock();
        try {
            return realizedPnl;
        } finally {
            lock.unlock();
        }
    }

    public double getTotalFees() {
        lock.lock();
        try {
            return totalFees;
        } finally {
            lock.unlock();
        }
    }

    public int getTradeCount() {
        lock.lock();
        try {
            return tradeCount;
        } finally {
            lock.unlock();
        }
    }

    public int getShortPosition() {
        lock.lock();
        try {
            return shortPosition;
        } finally {
            lock.unlock();
        }
    }

    public int getBorrowedShares() {
        lock.lock();
        try {
            return borrowedShares;
        } finally {
            lock.unlock();
        }
    }

    public double getMargin() {
        lock.lock();
        try {
            return margin;
        } finally {
            lock.unlock();
        }
    }

    public double getLoanInterest() {
        lock.lock();
        try {
            return loanInterest;
        } finally {
            lock.unlock();
        }
    }

    public double getMaintenanceMarginRate() {
        lock.lock();
        try {
            return maintenanceMarginRate;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("Account{%s cash=%.2f pos=%d short=%d margin=%.2f pnl=%.2f}",
                name, getCash(), getPosition(), getShortPosition(), getMargin(), getRealizedPnl());
    }
}
