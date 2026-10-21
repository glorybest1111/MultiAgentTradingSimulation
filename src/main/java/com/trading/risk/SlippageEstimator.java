package com.trading.risk;

import com.trading.engine.OrderBook;
import com.trading.model.Order;
import com.trading.model.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑点估算器：撮合路径之外的**只读报价工具**。
 *
 * <p>历史问题：原先 {@code walkBook} 放在 {@link SlippageModel} 里，
 * 容易让人误以为「撮合时会按层级行走算加权均价」。实际上撮合引擎是
 * <b>逐档按挂单价逐笔成交</b>的（见 MatchingEngine），只有当簿被吃空后才进入虚拟深度。
 *
 * <p>这里把它独立出来，明确定位为：
 * <ul>
 *   <li>对未来成交平均价的<b>事前估算</b>（GUI 预览、AI 决策参考）</li>
 *   <li>测试与教学的参照实现（作业示例：100 股 @100 + 200 股 @101 → 均价 100.67）</li>
 * </ul>
 * 它不参与任何撮合，也不改变订单簿状态。
 */
public final class SlippageEstimator {

    private SlippageEstimator() {
    }

    /**
     * 按订单簿层级行走，计算吃掉 quantity 数量挂单的平均成交价。
     *
     * @param book        订单簿
     * @param restingSide 被吃的一侧（买挂单填 Side.BUY，卖挂单填 Side.SELL）
     * @param quantity    主动方数量
     * @return 平均成交价；簿为空或口径不一致时返回 NaN
     */
    public static double walkBook(OrderBook book, Side restingSide, int quantity) {
        return walkBook(book, restingSide, quantity, false);
    }

    /**
     * @param allowPartial true 时允许簿深不足，按实际可成交量计算均价；
     *                     false 时深度不足直接返回 NaN（明确告知无法足额成交）
     */
    public static double walkBook(OrderBook book, Side restingSide, int quantity, boolean allowPartial) {
        if (book == null || quantity <= 0) {
            return Double.NaN;
        }
        List<Order> levels = book.orderedSnapshot(restingSide);
        if (!allowPartial && availableDepth(levels) < quantity) {
            return Double.NaN;
        }
        double totalValue = 0;
        int filled = 0;
        for (Order level : levels) {
            if (filled >= quantity) {
                break;
            }
            int take = Math.min(level.effectiveQuantity(), quantity - filled);
            totalValue += level.getPrice() * take;
            filled += take;
        }
        if (filled == 0) {
            return Double.NaN;
        }
        return totalValue / filled;
    }

    /** 某一侧在簿可见深度内的可成交总量（冰山单只统计可见切片） */
    public static int availableDepth(OrderBook book, Side side) {
        if (book == null) {
            return 0;
        }
        return availableDepth(book.orderedSnapshot(side));
    }

    private static int availableDepth(List<Order> levels) {
        int total = 0;
        for (Order level : levels) {
            total += level.effectiveQuantity();
        }
        return total;
    }

    /** 簿快照：按撮合优先顺序的前 depth 档 [{price, qty}]，供 GUI 五档盘口使用 */
    public static List<double[]> topLevels(OrderBook book, Side side, int depth) {
        List<double[]> result = new ArrayList<>();
        if (book == null) {
            return result;
        }
        for (Order order : book.orderedSnapshot(side)) {
            if (result.size() >= depth) {
                break;
            }
            result.add(new double[]{order.getPrice(), order.effectiveQuantity()});
        }
        return result;
    }
}
