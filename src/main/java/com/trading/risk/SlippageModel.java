package com.trading.risk;

import com.trading.engine.OrderBook;
import com.trading.model.Order;
import com.trading.model.Side;

import java.util.List;

/**
 * 滑点模型：订单过大或簿深不足时，实际成交价相对最优价恶化。
 * 示例：市场卖单 100 股 @100 + 200 股 @101，买入 300 股的平均成本 = 100.67（真实市场行为）。
 *
 * 虚拟流动性：订单簿被吃空后，市价单可进入虚拟深度——
 * 共 maxLevels 层、每层 depthPerLevel 股，价格逐层恶化 perLevel；
 * 虚拟深度耗尽后市价单剩余部分取消（模拟真实市场中流动性不足导致的部分成交）。
 */
public class SlippageModel {

    /** 虚拟深度每深入一层，成交价恶化的幅度 */
    private final double perLevel;

    /** 虚拟深度层数与每层容量 */
    private final int maxLevels;
    private final int depthPerLevel;

    public SlippageModel(double perLevel) {
        this(perLevel, 2, 10);
    }

    public SlippageModel(double perLevel, int maxLevels, int depthPerLevel) {
        this.perLevel = perLevel;
        this.maxLevels = maxLevels;
        this.depthPerLevel = depthPerLevel;
    }

    /** 簿深耗尽后第 level 层虚拟流动性的成交价（买向上加价，卖向下减价） */
    public double priceAfterLevels(double basePrice, int level, Side side) {
        double delta = perLevel * level;
        return side == Side.BUY ? basePrice + delta : basePrice - delta;
    }

    /** 虚拟深度总容量（每期重置） */
    public int virtualCapacity() {
        return maxLevels * depthPerLevel;
    }

    /** 虚拟深度每层容量 */
    public int getDepthPerLevel() {
        return depthPerLevel;
    }

    /** 已用股数对应的虚拟层数（从 1 开始） */
    public int levelFor(int usedQuantity) {
        return Math.min(maxLevels, usedQuantity / depthPerLevel + 1);
    }

    /** 第 level 层内剩余可成交数量 */
    public int remainingInLevel(int usedQuantity) {
        return depthPerLevel - usedQuantity % depthPerLevel;
    }

    /**
     * 按订单簿层级行走，计算吃掉 quantity 数量挂单的平均成交价。
     *
     * @param book        订单簿
     * @param restingSide 被吃的一侧（买挂单填 Side.BUY，卖挂单填 Side.SELL）
     * @param quantity    主动方数量
     * @return 平均成交价；簿为空时返回 NaN
     */
    public static double walkBook(OrderBook book, Side restingSide, int quantity) {
        List<Order> levels = book.orderedSnapshot(restingSide);
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

    public double getPerLevel() {
        return perLevel;
    }

}
