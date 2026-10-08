package com.trading.test;

import com.trading.engine.MatchingEngine;
import com.trading.model.Order;
import com.trading.model.OrderType;
import com.trading.model.Side;

/**
 * 入门演示：一次最简单的撮合（卖单挂单 → 买单主动成交）。
 * 完整系统入口见 com.trading.Main。
 */
public class SimulationMain {

    public static void main(String[] args) {
        MatchingEngine engine = new MatchingEngine();
        engine.setLastPrice(150);

        Order sell = new Order("AAPL", Side.SELL, OrderType.LIMIT, 150, 100, "Alice");
        engine.submitOrder(sell);
        System.out.println("Alice 挂出卖单 100 股 @ 150");

        Order buy = new Order("AAPL", Side.BUY, OrderType.LIMIT, 151, 50, "Bob");
        System.out.println("Bob 提交买单 50 股 @ 151");
        System.out.println("撮合结果: " + engine.submitOrder(buy));
    }

}
