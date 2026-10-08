package com.trading.engine;

import com.trading.model.Order;
import com.trading.model.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 订单簿：股票市场的核心数据结构。
 * 撮合规则：买单价格高优先，卖单价格低优先；同价格时先到先得（时间优先）。
 */
public class OrderBook {

    // 买单：价格高优先；卖单：价格低优先；同价按时间优先，时间相同按订单号
    private final PriorityQueue<Order> bids = new PriorityQueue<>((a, b) -> {
        int priceCompare = Double.compare(b.getPrice(), a.getPrice());
        if (priceCompare != 0) {
            return priceCompare;
        }
        int timeCompare = a.getTimestamp().compareTo(b.getTimestamp());
        if (timeCompare != 0) {
            return timeCompare;
        }
        return Long.compare(a.getOrderId(), b.getOrderId());
    });

    private final PriorityQueue<Order> asks = new PriorityQueue<>((a, b) -> {
        int priceCompare = Double.compare(a.getPrice(), b.getPrice());
        if (priceCompare != 0) {
            return priceCompare;
        }
        int timeCompare = a.getTimestamp().compareTo(b.getTimestamp());
        if (timeCompare != 0) {
            return timeCompare;
        }
        return Long.compare(a.getOrderId(), b.getOrderId());
    });

    public void addOrder(Order order) {
        if (order.getSide() == Side.BUY) {
            bids.add(order);
        } else {
            asks.add(order);
        }
    }

    /** 撤单，返回是否撤销成功 */
    public boolean removeOrder(Order order) {
        return (order.getSide() == Side.BUY ? bids : asks).remove(order);
    }

    public Order getBestBid() {
        return bids.peek();
    }

    public Order getBestAsk() {
        return asks.peek();
    }

    /** 买卖价差；任一侧为空时返回 NaN */
    public double spread() {
        Order bid = bids.peek();
        Order ask = asks.peek();
        if (bid == null || ask == null) {
            return Double.NaN;
        }
        return ask.getPrice() - bid.getPrice();
    }

    /** 某一侧按撮合优先顺序的订单快照（用于滑点估算等） */
    public List<Order> orderedSnapshot(Side side) {
        PriorityQueue<Order> queue = side == Side.BUY ? bids : asks;
        List<Order> snapshot = new ArrayList<>();
        while (!queue.isEmpty()) {
            snapshot.add(queue.poll());
        }
        queue.addAll(snapshot);
        return snapshot;
    }

    /** 某一侧的挂单总量（冰山单只统计可见部分） */
    public int totalQuantity(Side side) {
        PriorityQueue<Order> queue = side == Side.BUY ? bids : asks;
        int total = 0;
        for (Order order : queue) {
            total += order.effectiveQuantity();
        }
        return total;
    }

    /** 某交易员的全部挂单 */
    public List<Order> ordersOf(String traderName) {
        List<Order> result = new ArrayList<>();
        for (Order order : bids) {
            if (order.getTraderName().equals(traderName)) {
                result.add(order);
            }
        }
        for (Order order : asks) {
            if (order.getTraderName().equals(traderName)) {
                result.add(order);
            }
        }
        return result;
    }

    public PriorityQueue<Order> getBids() {
        return bids;
    }

    public PriorityQueue<Order> getAsks() {
        return asks;
    }

}
