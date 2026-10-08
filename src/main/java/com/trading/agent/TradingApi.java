package com.trading.agent;

import com.trading.model.Order;

/**
 * 交易接口：Agent 通过它下单与撤单，由仿真系统实现。
 */
public interface TradingApi {

    /** 提交订单并立即撮合，返回订单（可用于后续撤单） */
    Order placeOrder(Order order);

    /** 撤单，返回是否撤销成功 */
    boolean cancelOrder(Order order);

}
