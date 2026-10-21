package com.trading.agent;

import com.trading.model.Order;

import java.util.List;

/**
 * 交易接口：Agent 通过它下单与撤单，由仿真系统实现。
 *
 * <p>除下单/撤单外，还暴露「查询自己未成交挂单」能力——
 * AI 托管需要在突发利空时自动撤销用户挂出的限价单。
 */
public interface TradingApi {

    /** 提交订单并立即撮合，返回订单（可用于后续撤单） */
    Order placeOrder(Order order);

    /** 撤单，返回是否撤销成功 */
    boolean cancelOrder(Order order);

    /** 当前交易者名下尚未成交的订单（挂单 + 待触发止损单） */
    default List<Order> openOrders() {
        return List.of();
    }

    /** 市场当前是否处于熔断暂停状态 */
    default boolean isTradingHalted() {
        return false;
    }
}
