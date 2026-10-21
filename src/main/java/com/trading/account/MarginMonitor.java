package com.trading.account;

import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 保证金监控与强制平仓（爆仓）处理。
 *
 * <p>每期结算后扫描所有账户：当空头账户的权益 / 空头市值 低于维持保证金率时，
 * 生成一张<b>市价买入平仓单</b>把空头全部买回（同时释放占用的保证金）。
 * 返回的订单由 {@code MarketSimulation} 提交给撮合引擎执行。
 */
public class MarginMonitor {

    private final Map<String, Account> accounts;
    private final double interestRatePerTick;
    private int liquidationCount;

    public MarginMonitor(Map<String, Account> accounts, double interestRatePerTick) {
        this.accounts = accounts;
        this.interestRatePerTick = interestRatePerTick;
    }

    /** 计提所有空头账户的借券利息，返回本期利息合计 */
    public double accrueInterest(double price) {
        double total = 0;
        for (Account account : accounts.values()) {
            total += account.accrueBorrowInterest(price, interestRatePerTick);
        }
        return total;
    }

    /**
     * 检查强平，返回需要执行的平仓订单。
     *
     * @param symbol     标的代码
     * @param markPrice  用于计算权益的最新价
     * @param nowMillis  时间戳
     */
    public List<Order> checkMarginCalls(String symbol, double markPrice, long nowMillis) {
        List<Order> liquidations = new ArrayList<>();
        for (Account account : accounts.values()) {
            if (!account.isMarginCall(markPrice)) {
                continue;
            }
            int shortPosition = account.getShortPosition();
            if (shortPosition <= 0) {
                continue;
            }
            // 必须用真实 trader 名称下单，否则结算时找不到对应账户
            liquidations.add(new Order(symbol, Side.BUY, OrderType.MARKET, markPrice, shortPosition,
                    account.getName()));
            liquidationCount++;
        }
        return liquidations;
    }

    public int getLiquidationCount() {
        return liquidationCount;
    }
}
